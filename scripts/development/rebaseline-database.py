"""Đối chiếu cấu trúc rồi đổi history ngoài runtime; mặc định chỉ kiểm và rollback.

Nhận kết nối bằng PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD/PGSSLMODE chuẩn psql.
Không tự đọc .env, không in lỗi SQL có thể chứa dữ liệu và không chạy seed người dùng.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "src/main/resources/db/migration"
RESOURCE = re.compile(r"postgresql/(auth|catalog|carrier|trip|booking|common|foundation)/([0-9]{3}_[a-z0-9_]+\.sql)")


def baseline():
    """Cùng manifest/checksum byte gốc với ứng dụng, không nhận SQL/path từ client."""
    scripts = []
    for line in (MIGRATIONS / "postgresql/migrations.list").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        match = RESOURCE.fullmatch(line.strip())
        if not match:
            raise ValueError("Manifest không hợp lệ")
        data = (MIGRATIONS / match.group()).read_bytes()
        scripts.append(("/".join(match.groups()), hashlib.sha256(data).hexdigest(), data.decode("utf-8")))
    if not scripts or len({name for name, _, _ in scripts}) != len(scripts):
        raise ValueError("Manifest rỗng hoặc trùng version")
    return scripts


SIGNATURE = r"""
CREATE TEMP TABLE baseline_scope(value TEXT) ON COMMIT DROP;
CREATE OR REPLACE FUNCTION pg_temp.normal_sql(value TEXT, ns TEXT) RETURNS TEXT LANGUAGE SQL STABLE AS $fn$
    SELECT replace(replace(replace(value, quote_ident(ns)||'.', ''), ns||'.', ''),
        coalesce((SELECT quote_ident(n.nspname)||'.gin_trgm_ops'
            FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='pg_trgm'),
            '__no_extension__'), 'gin_trgm_ops')
$fn$;
-- Reparse định nghĩa catalog trong schema tham chiếu để khác bản vá PostgreSQL không tạo
-- false mismatch giữa cast cả mảng và cast từng literal. Không bỏ cast/điều kiện bằng regex.
CREATE OR REPLACE FUNCTION pg_temp.canonical_check(definition TEXT, ref TEXT, tab TEXT, name TEXT)
RETURNS TEXT LANGUAGE plpgsql VOLATILE AS $fn$
DECLARE temporary_name TEXT := 'vr_check_' || md5(tab||name); result TEXT;
BEGIN
    EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I %s',ref,tab,temporary_name,definition);
    SELECT pg_temp.normal_sql(pg_get_constraintdef(k.oid,true),ref) INTO result
        FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname=ref AND c.relname=tab AND k.conname=temporary_name;
    EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I',ref,tab,temporary_name);
    RETURN result;
END $fn$;
CREATE OR REPLACE FUNCTION pg_temp.canonical_index(definition TEXT, ref TEXT, tab TEXT, name TEXT, unique_index BOOLEAN)
RETURNS TEXT LANGUAGE plpgsql VOLATILE AS $fn$
DECLARE temporary_name TEXT := 'vr_index_' || md5(tab||name); result TEXT; body_at INTEGER;
BEGIN
    body_at := strpos(definition,' USING ');
    IF body_at=0 THEN RAISE EXCEPTION 'BASELINE_INDEX_DEFINITION_UNSUPPORTED'; END IF;
    EXECUTE format('CREATE %s INDEX %I ON %I.%I %s',
        CASE WHEN unique_index THEN 'UNIQUE' ELSE '' END,temporary_name,ref,tab,substr(definition,body_at+1));
    SELECT replace(pg_temp.normal_sql(pg_get_indexdef(c.oid),ref),quote_ident(temporary_name),quote_ident(name))
        INTO result FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname=ref AND c.relname=temporary_name;
    EXECUTE format('DROP INDEX %I.%I',ref,temporary_name);
    RETURN result;
END $fn$;
CREATE OR REPLACE FUNCTION pg_temp.schema_signature(ns TEXT, reference_schema TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE SQL VOLATILE AS $fn$
    SELECT jsonb_build_object(
        'tables', (SELECT jsonb_agg(c.relname ORDER BY c.relname)
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=ns AND c.relkind IN ('r','p')),
        'columns', (SELECT jsonb_agg(jsonb_build_array(c.relname,a.attname,
                format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attidentity,a.attgenerated,
                pg_temp.normal_sql(pg_get_expr(d.adbin,d.adrelid),ns)) ORDER BY c.relname,a.attnum)
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_attribute a ON a.attrelid=c.oid
            LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
            WHERE n.nspname=ns AND c.relkind IN ('r','p') AND a.attnum>0 AND NOT a.attisdropped),
        'constraints', (SELECT jsonb_agg(jsonb_build_array(c.relname,k.conname,k.contype,
                k.convalidated,k.condeferrable,k.condeferred,
                CASE WHEN reference_schema IS NOT NULL AND k.contype='c'
                    THEN pg_temp.canonical_check(pg_get_constraintdef(k.oid,true),reference_schema,c.relname,k.conname)
                    ELSE pg_temp.normal_sql(pg_get_constraintdef(k.oid,true),ns) END) ORDER BY c.relname,k.conname)
            FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=ns AND k.contype IN ('p','u','c','f','x')),
        'indexes', (SELECT jsonb_agg(jsonb_build_array(c.relname,ix.relname,i.indisvalid,
                CASE WHEN reference_schema IS NOT NULL
                    THEN pg_temp.canonical_index(pg_get_indexdef(i.indexrelid),reference_schema,c.relname,ix.relname,i.indisunique)
                    ELSE pg_temp.normal_sql(pg_get_indexdef(i.indexrelid),ns) END) ORDER BY c.relname,ix.relname)
            FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid
            JOIN pg_class ix ON ix.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=ns),
        'sequences', (SELECT jsonb_agg(jsonb_build_array(c.relname,q.seqtypid::regtype::text,
                q.seqstart,q.seqincrement,q.seqmax,q.seqmin,q.seqcache,q.seqcycle)
                ORDER BY c.relname)
            FROM pg_sequence q JOIN pg_class c ON c.oid=q.seqrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=ns)
    )
$fn$;
"""


def sql(schema, apply=False):
    """Một transaction/advisory lock; khác cấu trúc thì rollback trước DELETE history."""
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", schema) or schema.startswith("pg_") or schema in {"public", "information_schema"}:
        raise ValueError("Schema ứng dụng không hợp lệ")
    scratch = "vr_baseline_" + uuid.uuid4().hex
    scripts = baseline()
    setup = f"""
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
SELECT pg_advisory_xact_lock(781205610);
SET LOCAL search_path="{schema}",pg_temp;
DO $check$ BEGIN
    IF to_regclass('vietroute_schema_history') IS NULL THEN
        RAISE EXCEPTION 'BASELINE_HISTORY_REQUIRED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM auth_users WHERE id=0 AND username='system' AND NOT active) THEN
        RAISE EXCEPTION 'BASELINE_SYSTEM_REQUIRED';
    END IF;
END $check$;
DO $lock$ DECLARE item RECORD; BEGIN
    FOR item IN SELECT tablename FROM pg_tables WHERE schemaname='{schema}' ORDER BY tablename LOOP
        EXECUTE format('LOCK TABLE %I.%I IN SHARE MODE','{schema}',item.tablename);
    END LOOP;
END $lock$;
CREATE SCHEMA "{scratch}";
SET LOCAL search_path="{scratch}",pg_temp;
CREATE TABLE vietroute_schema_history(version VARCHAR(40) PRIMARY KEY, checksum VARCHAR(64) NOT NULL,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
"""
    # psql gửi từng statement riêng khi đọc stdin; gom mỗi resource thành một lượt gửi để tránh
    # hàng trăm round-trip qua mạng/pooler. Nội dung và checksum file gốc vẫn giữ nguyên.
    setup += "\n".join("DO $baseline$ BEGIN EXECUTE $resource$" + body
        + "$resource$; END $baseline$;" for _, _, body in scripts) + "\n" + SIGNATURE
    setup += f"""
DO $compare$ BEGIN
    IF pg_temp.schema_signature('{schema}','{scratch}') IS DISTINCT FROM pg_temp.schema_signature('{scratch}') THEN
        RAISE EXCEPTION 'BASELINE_STRUCTURE_MISMATCH';
    END IF;
END $compare$;
SET LOCAL search_path="{schema}",pg_temp;
"""
    if apply:
        values = ",".join(f"('{name}','{checksum}')" for name, checksum, _ in scripts)
        setup += ("DO $history$ BEGIN IF (SELECT count(*) FROM vietroute_schema_history) <> " + str(len(scripts))
            + " OR EXISTS (SELECT 1 FROM vietroute_schema_history h LEFT JOIN (VALUES " + values
            + ") AS expected(version,checksum) ON expected.version=h.version WHERE expected.checksum IS DISTINCT FROM h.checksum) THEN\n"
            + "DELETE FROM vietroute_schema_history;\n")
        for name, checksum, _ in scripts:
            setup += f"INSERT INTO vietroute_schema_history(version,checksum) VALUES ('{name}','{checksum}');\n"
        setup += "END IF; END $history$;\n"
        # CASCADE chỉ tác động schema tham chiếu random do chính transaction vừa tạo, không phải schema đích.
        setup += f'DROP SCHEMA "{scratch}" CASCADE;\n'
        # Transaction pooler có thể giữ backend sau khi client đóng: dọn mọi helper trước commit,
        # không trông chờ kết thúc session xóa pg_temp và không để lần bảo trì sau đụng tên hàm.
        setup += ("DROP FUNCTION pg_temp.schema_signature(TEXT,TEXT);\n"
            "DROP FUNCTION pg_temp.canonical_check(TEXT,TEXT,TEXT,TEXT);\n"
            "DROP FUNCTION pg_temp.canonical_index(TEXT,TEXT,TEXT,TEXT,BOOLEAN);\n"
            "DROP FUNCTION pg_temp.normal_sql(TEXT,TEXT);\nCOMMIT;\n")
    else:
        setup += "ROLLBACK;\n"
    return setup


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", default="vietroute")
    parser.add_argument("--psql", default="psql")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    parser.add_argument("--confirm-stopped", action="store_true", help="Xác nhận đã dừng mọi node và có backup kiểm phục hồi")
    args = parser.parse_args()
    if args.apply and not args.confirm_stopped:
        parser.error("--apply cần --confirm-stopped sau khi backup/kiểm phục hồi và dừng mọi node")
    if not os.environ.get("PGHOST") or not os.environ.get("PGDATABASE") or not os.environ.get("PGUSER"):
        parser.error("Cần PGHOST, PGDATABASE, PGUSER rõ ràng; không dùng database mặc định hoặc tự đọc .env")
    try:
        statement = sql(args.schema, args.apply)
        env = os.environ.copy()
        env.update(PGCLIENTENCODING="UTF8", PGCONNECT_TIMEOUT="10")
        result = subprocess.run([args.psql, "-XqAtw", "-v", "ON_ERROR_STOP=1"],
            input=statement.encode("utf-8"), stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
    except (ValueError, OSError):
        print("Không thể chuẩn bị baseline/psql; kiểm đường dẫn, manifest và schema.", file=sys.stderr)
        return 1
    if result.returncode:
        # Không in stderr thô: driver/server có thể kèm connection/user hoặc chi tiết private.
        reason = "cấu trúc database khác baseline" if b"BASELINE_STRUCTURE_MISMATCH" in result.stderr else "kết nối/quyền/khóa hoặc cấu trúc không hợp lệ"
        print("Không thay history: " + reason + ". Transaction đã rollback.", file=sys.stderr)
        return 1
    print("Đã đổi history theo baseline; dữ liệu ứng dụng giữ nguyên." if args.apply else "Cấu trúc khớp baseline; đã rollback, chưa đổi history.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
