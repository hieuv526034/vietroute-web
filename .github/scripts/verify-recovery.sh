#!/usr/bin/env bash
# Chỉ được source cuối bộ kiểm Docker CI; dump/restore dữ liệu giả, không nhận URL hoặc credentials production.
[[ "${GITHUB_ACTIONS:-}" == true && "${work:-}" == "${RUNNER_TEMP:?}/vietroute-auth" ]] || {
  printf 'Recovery drill chỉ chạy trong Docker CI đã cô lập.\n' >&2
  exit 1
}
printf 'Recovery stage: offline snapshot, complete table/sequence comparison and restored application login\n'
# Dừng đúng ba node CI để manifest và snapshot đứng yên; PostgreSQL CI vẫn chạy.
docker stop vietroute-auth-ci vietroute-auth-ci2 vietroute-pooler-ci >/dev/null
recovery_started=$SECONDS
recovery_manifest() {
  docker exec -i vietroute-db-ci psql -X -U vietroute_ci -d "$1" -v ON_ERROR_STOP=1 -At <<'SQL'
SELECT format(
  'SELECT %L || ''|'' || count(*) || ''|'' || md5(COALESCE(string_agg(md5(row_to_json(t)::text),'''' ORDER BY md5(row_to_json(t)::text)),'''')) FROM %I.%I t;',
  schemaname || '.' || tablename, schemaname, tablename)
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog','information_schema')
ORDER BY schemaname,tablename
\gexec
SELECT format('SELECT %L || ''|'' || last_value || ''|'' || is_called FROM %I.%I;',
  schemaname || '.' || sequencename, schemaname, sequencename)
FROM pg_sequences WHERE schemaname NOT IN ('pg_catalog','information_schema')
ORDER BY schemaname,sequencename
\gexec
SQL
}
recovery_constraints() {
  docker exec -i vietroute-db-ci psql -X -U vietroute_ci -d "$1" -v ON_ERROR_STOP=1 -At <<'SQL'
SELECT n.nspname || '.' || c.relname || '|' || k.conname || '|' || k.convalidated || '|' || pg_get_constraintdef(k.oid)
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname NOT IN ('pg_catalog','information_schema')
ORDER BY n.nspname,c.relname,k.conname;
SQL
}
recovery_manifest vietroute_ci > "$work/recovery-before.txt"
docker exec vietroute-db-ci pg_dump -U vietroute_ci -d vietroute_ci --format=custom --file=/tmp/vietroute-recovery-ci.dump
docker exec vietroute-db-ci pg_dump -U vietroute_ci -d vietroute_ci --format=custom --schema-only --file=/tmp/vietroute-recovery-schema-ci.dump
docker exec vietroute-db-ci createdb -U vietroute_ci vietroute_recovery_ci
docker exec vietroute-db-ci createdb -U vietroute_ci vietroute_recovery_schema_ci
docker exec vietroute-db-ci pg_restore -U vietroute_ci --dbname=vietroute_recovery_ci --exit-on-error /tmp/vietroute-recovery-ci.dump
docker exec vietroute-db-ci pg_restore -U vietroute_ci --dbname=vietroute_recovery_schema_ci --exit-on-error /tmp/vietroute-recovery-schema-ci.dump
recovery_manifest vietroute_recovery_ci > "$work/recovery-after.txt"
cmp --silent "$work/recovery-before.txt" "$work/recovery-after.txt"
# PostgreSQL có thể chuẩn hóa AND/cast ARRAY khi parse lại CHECK. DB tham chiếu chỉ schema cũng đi qua
# parser restore như DB dữ liệu, để so đầy đủ định nghĩa ràng buộc mà không bỏ/regex nới lỏng biểu thức.
recovery_constraints vietroute_recovery_schema_ci > "$work/recovery-constraints-reference.txt"
recovery_constraints vietroute_recovery_ci > "$work/recovery-constraints-restored.txt"
cmp --silent "$work/recovery-constraints-reference.txt" "$work/recovery-constraints-restored.txt"
# Manifest gồm toàn bộ dòng, history/checksum/hash và sequence/FK; không in giá trị hoặc dump thành artifact.
recovery_sql() { docker exec -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -X -U vietroute_ci -d vietroute_recovery_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(recovery_sql 'SELECT count(*) FROM vietroute_schema_history')" == 23 ]]
recovery_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/recovery-hash-before"
start_app vietroute-recovery-ci 18083 vietroute_recovery_ci
base=http://127.0.0.1:18083
curl --fail --silent --show-error --retry 30 --retry-delay 1 --retry-all-errors --retry-connrefused --max-time 5 "$base/health" >/dev/null
rm -f "$work/cookies"
refresh_csrf
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
grep -qi '^Server-Timing: auth;dur=[0-9]' "$work/headers"
token=$(field data.accessToken)
call 200 GET /api/v1/auth/me '' bearer
call 200 GET /dashboard '' cookie
call 200 GET /api/v1/admin/access/registry/roles/page '' bearer
recovery_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/recovery-hash-after"
cmp --silent "$work/recovery-hash-before" "$work/recovery-hash-after"
printf 'CI recovery passed: all table data, sequences and constraints matched; 23 migrations and restored auth/page/API passed; elapsed=%ss. Not production RPO/RTO or scheduled backup.\n' "$((SECONDS-recovery_started))"
