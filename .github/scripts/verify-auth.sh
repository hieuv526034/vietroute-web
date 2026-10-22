#!/usr/bin/env bash
# Kiểm tra tích hợp bằng PostgreSQL tạm; không đọc secrets hoặc kết nối Neon production.
set -Eeuo pipefail
trap 'printf "Auth verification failed near line %s\n" "$LINENO" >&2' ERR
work="${RUNNER_TEMP:?}/vietroute-auth"
mkdir -p "$work"
db_password="$(openssl rand -hex 24)"
admin_password="CiA1!$(openssl rand -hex 16)"
next_password="CiB2!$(openssl rand -hex 16)"
jwt_secret="$(openssl rand -hex 48)"
printf '::add-mask::%s\n' "$db_password" "$admin_password" "$next_password" "$jwt_secret"
docker rm --force vietroute-ci >/dev/null
docker network create vietroute-auth-net >/dev/null
docker run --detach --name vietroute-db-ci --network vietroute-auth-net \
  -e POSTGRES_DB=vietroute_ci -e POSTGRES_USER=vietroute_ci -e POSTGRES_PASSWORD="$db_password" postgres:17-alpine >/dev/null
for attempt in {1..30}; do
  if docker exec vietroute-db-ci pg_isready -U vietroute_ci -d vietroute_ci >/dev/null 2>&1; then break; fi
  sleep 1
done
start_app() {
  local database="${3:-vietroute_ci}"
  docker run --detach --name "$1" --network vietroute-auth-net --memory 512m \
    -e PORT=10001 -p "127.0.0.1:$2:10001" \
    -e VIETROUTE_ENV=production -e VIETROUTE_COOKIE_SECURE=false -e VIETROUTE_DB_INIT_ENABLED=true \
    -e VIETROUTE_WORKER_ENABLED=true -e VIETROUTE_WORKER_POLL_SECONDS=1 \
    -e VIETROUTE_RATE_REQUESTS="${4:-300}" -e VIETROUTE_RATE_LOGINS=200 \
    -e VIETROUTE_REDIS_URL="${5:-}" \
    -e VIETROUTE_DB_URL="jdbc:postgresql://vietroute-db-ci:5432/$database" \
    -e VIETROUTE_DB_USER=vietroute_ci -e VIETROUTE_DB_PASSWORD="$db_password" \
    -e VIETROUTE_JWT_SECRET="$jwt_secret" -e VIETROUTE_BOOTSTRAP_ADMIN_USERNAME=admin \
    -e VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" vietroute-web:ci >/dev/null
}
# Hai instance khởi động cùng lúc để kiểm tra khóa migration/seed và chống tạo trùng admin.
start_app vietroute-auth-ci 18080
start_app vietroute-auth-ci2 18081
for port in 18080 18081; do
  curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
    --retry-connrefused --max-time 5 "http://127.0.0.1:$port/health" > /dev/null
done
sql() { docker exec -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -U vietroute_ci -d vietroute_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='admin'")" == 1 ]]
[[ "$(sql 'SELECT count(*) FROM vietroute_schema_history')" == 3 ]]
[[ "$(sql 'SELECT count(*) FROM catalog_items')" == 5 ]]
[[ "$(sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('auth_roles','auth_users','auth_user_roles','catalog_items','vietroute_schema_history')")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM pg_indexes WHERE schemaname='vietroute' AND indexname IN ('idx_auth_users_created_by','idx_auth_users_updated_by','idx_auth_user_roles_role_code','idx_catalog_created_by','idx_catalog_updated_by')")" == 5 ]]
docker rm --force vietroute-auth-ci2 >/dev/null
base=http://127.0.0.1:18080
result="$work/response.json"
csrf_token=''
token=''
call() {
  local expected="$1" method="$2" path="$3" payload="${4:-}" mode="${5:-none}"
  # Mô phỏng client Axios: lấy lại token hiện hành trước mỗi mutation bằng cookie.
  if [[ "$mode" == cookie && "$method" != GET && "$method" != HEAD && "$method" != OPTIONS ]]; then refresh_csrf; fi
  local args=(--silent --show-error --max-time 30 --output "$result" --dump-header "$work/headers" --write-out '%{http_code}' -X "$method" -H 'X-Locale: en')
  if [[ "$mode" == cookie* ]]; then args+=(-b "$work/cookies" -c "$work/cookies"); fi
  if [[ "$mode" == cookie ]]; then args+=(-H "X-XSRF-TOKEN: $csrf_token"); fi
  if [[ "$mode" == bearer ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$payload" ]]; then args+=(-H 'Content-Type: application/json' --data-binary "@$payload"); fi
  local actual
  actual=$(curl "${args[@]}" "$base$path")
  if [[ "$actual" != "$expected" ]]; then
    printf 'Unexpected HTTP status: %s %s expected=%s actual=%s\n' "$method" "$path" "$expected" "$actual" >&2
    python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("Error code:", d.get("code"), "key:", d.get("messageKey"))' "$result" >&2 || true
    exit 1 # Không in response/token/password ra log kể cả khi assertion thất bại.
  fi
}
field() { python3 -c 'import json,sys; v=json.load(open(sys.argv[1]));
for k in sys.argv[2].split("."): v=v[k]
print(v)' "$result" "$1"; }
assert_code() { [[ "$(field code)" == "$1" ]]; }
refresh_csrf() {
  call 200 GET /api/v1/auth/csrf '' cookie
  csrf_token=$(field data.token)
}
login_body() {
  CI_USERNAME="$1" CI_PASSWORD="$2" python3 -c 'import json,os,sys; json.dump({"username":os.environ["CI_USERNAME"],"password":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/login.json"
}
refresh_csrf
printf 'Auth stage: initial login, cookies and public catalog\n'
login_body admin "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
initial_token="$token"
grep -qi 'HttpOnly' "$work/headers"
grep -qi 'SameSite=Lax' "$work/headers"
[[ "$(field data.user.mustChangePassword)" == True ]]
call 200 GET /api/v1/auth/me '' cookie
[[ "$(field data.username)" == admin ]]
call 200 GET /api/v1/auth/me '' bearer
call 401 GET /api/v1/auth/me
assert_code AUTH002
call 200 GET '/api/v1/catalogs/VEHICLE_TYPE?lang=en'
[[ "$(field locale)" == en ]]
# Nhiều request đồng thời vẫn dùng pool giới hạn, trả hết connection về trạng thái idle.
printf 'JDBC stage: concurrent reads and bounded pool\n'
pids=()
for attempt in {1..12}; do
  curl --fail --silent --show-error --max-time 30 "$base/api/v1/catalogs/VEHICLE_TYPE" > /dev/null &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid"; done
connections=$(sql "SELECT count(*) FROM pg_stat_activity WHERE datname='vietroute_ci' AND application_name='vietroute-web'")
[[ "$connections" -ge 1 && "$connections" -le 4 ]]
[[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE datname='vietroute_ci' AND application_name='vietroute-web' AND state='idle in transaction'")" == 0 ]]
python3 .github/scripts/check-load.py "$base"
call 400 GET '/api/v1/catalogs/VEHICLE_TYPE?size=101'
assert_code COM001
call 400 GET '/api/v1/catalogs/VEHICLE_TYPE?lang=fr'
assert_code COM003
cat > "$work/catalog.json" <<'JSON'
{"type":"VEHICLE_TYPE","code":"CITEST","nameVi":"Danh mục kiểm tra","nameEn":"CI catalog","sortOrder":10}
JSON
call 403 POST /api/v1/admin/catalogs "$work/catalog.json" cookie-no-csrf
assert_code AUTH006
printf 'Auth stage: catalog validation, audit and duplicate constraints\n'
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); d["sortOrder"]=None; json.dump(d,sys.stdout)' "$work/catalog.json" > "$work/null.json"
call 400 POST /api/v1/admin/catalogs "$work/null.json" bearer
assert_code COM002
printf '%s\n' '{"type":"VEHICLE_TYPE","type":"BOOKING_SOURCE"}' > "$work/duplicate-key.json"
call 400 POST /api/v1/admin/catalogs "$work/duplicate-key.json" bearer
assert_code COM002
call 201 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
[[ "$(field data.code)" == CITEST ]]
[[ "$(field data.createdBy)" == 1 ]]
call 409 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
assert_code COM005
sed 's/CITEST/bad-code!/' "$work/catalog.json" > "$work/invalid.json"
call 400 POST /api/v1/admin/catalogs "$work/invalid.json" bearer
assert_code COM001
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); del d["code"]; json.dump(d,sys.stdout)' "$work/catalog.json" > "$work/generated.json"
call 201 POST /api/v1/admin/catalogs "$work/generated.json" bearer
python3 -c 'import json,re,sys; d=json.load(open(sys.argv[1])); assert re.fullmatch(r"[A-Z][A-Z0-9]{2,39}",d["data"]["code"])' "$result"
CI_OLD="$admin_password" CI_NEW="$next_password" python3 -c 'import json,os,sys; json.dump({"currentPassword":os.environ["CI_OLD"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/password.json"
printf 'Auth stage: password change and token revocation\n'
call 200 POST /api/v1/auth/change-password "$work/password.json" cookie
new_token=$(field data.accessToken)
[[ "$(field data.user.mustChangePassword)" == False ]]
token="$initial_token"
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
token="$new_token"
call 200 GET /api/v1/auth/me '' bearer
call 200 POST /api/v1/auth/logout '' cookie
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
# Restart giữ nguyên hash đã đổi; không lấy mật khẩu bootstrap ghi đè lên tài khoản có sẵn.
sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/hash-before"
printf 'Auth stage: restart does not reset password\n'
docker restart vietroute-auth-ci >/dev/null
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 "$base/health" >/dev/null
sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/hash-after"
cmp --silent "$work/hash-before" "$work/hash-after"
refresh_csrf
login_body admin "$admin_password"
call 401 POST /api/v1/auth/login "$work/login.json" cookie
assert_code AUTH001
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
[[ "$(field data.user.mustChangePassword)" == False ]]
call 200 POST /api/v1/auth/logout '' cookie
# Tạo user CUSTOMER trong DB test riêng để kiểm tra quyền, khóa user và bộ đếm sai phải được commit.
sql "INSERT INTO auth_users(username,password_hash,display_name,must_change_password) SELECT 'customer',password_hash,'CI customer',false FROM auth_users WHERE username='admin'" >/dev/null
printf 'Auth stage: role checks and account lock\n'
sql "INSERT INTO auth_user_roles(user_id,role_code) SELECT id,'CUSTOMER' FROM auth_users WHERE username='customer'" >/dev/null
login_body customer "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 403 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
assert_code AUTH003
sql "UPDATE auth_users SET active=false WHERE username='customer'" >/dev/null
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
sql "UPDATE auth_users SET active=true WHERE username='customer'" >/dev/null
login_body customer WrongPassword2026!
for attempt in {1..4}; do call 401 POST /api/v1/auth/login "$work/login.json" cookie; done
call 429 POST /api/v1/auth/login "$work/login.json" cookie
assert_code AUTH005
[[ "$(sql "SELECT failed_login_attempts FROM auth_users WHERE username='customer'")" == 5 ]]
login_body customer "$next_password"
call 429 POST /api/v1/auth/login "$work/login.json" cookie
sql "UPDATE auth_users SET locked_until=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE username='customer'" >/dev/null
call 200 POST /api/v1/auth/login "$work/login.json" cookie
[[ "$(sql "SELECT failed_login_attempts FROM auth_users WHERE username='customer'")" == 0 ]]
customer_token=$(field data.accessToken)
printf 'Access stage: function permissions, optimistic updates and live revocation\n'
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
admin_token=$(field data.accessToken)
token="$admin_token"
call 200 GET /api/v1/admin/access/permissions '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert any(p["code"]=="CATALOG_WRITE" for p in d["data"])' "$result"
printf '%s\n' '{"permissions":["CATALOG_WRITE"],"version":0}' > "$work/permissions.json"
call 200 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/permissions.json" bearer
[[ "$(field data.version)" == 1 ]]
call 409 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/permissions.json" bearer
assert_code COM008
call 403 PUT /api/v1/admin/access/roles/ADMIN/permissions "$work/permissions.json" bearer
token="$customer_token"
sed 's/CITEST/PERMALLOW/' "$work/catalog.json" > "$work/permission-catalog.json"
call 201 POST /api/v1/admin/catalogs "$work/permission-catalog.json" bearer
call 403 GET /api/v1/admin/jobs '' bearer
token="$admin_token"
printf '%s\n' '{"permissions":[],"version":1}' > "$work/revoke.json"
call 200 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/revoke.json" bearer
token="$customer_token"
call 403 POST /api/v1/admin/catalogs "$work/permission-catalog.json" bearer
call 200 GET /api/v1/auth/me '' bearer
customer_id=$(field data.id)
customer_version=$(field data.version)
token="$admin_token"
printf '{"roles":["OPERATOR"],"version":%s}\n' "$customer_version" > "$work/roles.json"
call 200 PUT "/api/v1/admin/access/users/$customer_id/roles" "$work/roles.json" bearer
call 403 PUT /api/v1/admin/access/users/1/roles "$work/roles.json" bearer
token="$customer_token"
call 401 GET /api/v1/auth/me '' bearer
token="$admin_token"
call 200 GET /api/v1/admin/system/status '' bearer
[[ "$(field data.workerRunning)" == True ]]
printf '%s\n' '{"to":"nobody@example.invalid","subject":"CI only","text":"No real mail is sent"}' > "$work/mail.json"
# Mail tắt trong CI: không gọi Google/SMTP hoặc gửi đến người dùng thật.
actual=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -H 'Idempotency-Key: test-mail-key' \
  --data-binary "@$work/mail.json" "$base/api/v1/admin/mail")
[[ "$actual" == 503 ]]
assert_code MAIL001

printf 'Worker stage: concurrent claims, lease recovery, dead jobs and retry\n'
start_app vietroute-auth-ci2 18081
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 http://127.0.0.1:18081/health > /dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) SELECT ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'SYSTEM_CLEANUP','ci-cleanup-'||n,'{}' FROM generate_series(1,12) n" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000001','SYSTEM_CLEANUP','ci-cleanup-1','{}') ON CONFLICT DO NOTHING" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload,status,attempts,locked_until,lease_token) VALUES ('00000000-0000-0000-0000-000000000020','SYSTEM_CLEANUP','ci-expired','{}','RUNNING',1,CURRENT_TIMESTAMP-INTERVAL '1 minute','00000000-0000-0000-0000-000000000099')" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000021','UNKNOWN_TEST','ci-dead','{}')" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload,available_at) VALUES ('00000000-0000-0000-0000-000000000022','SYSTEM_CLEANUP','ci-future','{}',CURRENT_TIMESTAMP+INTERVAL '1 hour')" >/dev/null
sql "BEGIN; INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000023','SYSTEM_CLEANUP','ci-rollback','{}'); ROLLBACK" >/dev/null
for attempt in {1..40}; do
  if [[ "$(sql "SELECT count(*) FROM system_jobs WHERE status='SUCCEEDED'")" == 13 && "$(sql "SELECT status FROM system_jobs WHERE dedup_key='ci-dead'")" == DEAD ]]; then break; fi
  sleep 1
done
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE status='SUCCEEDED'")" == 13 ]]
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key LIKE 'ci-cleanup-%' AND attempts=1 AND payload='{}'::jsonb")" == 12 ]]
[[ "$(sql "SELECT attempts FROM system_jobs WHERE dedup_key='ci-expired'")" == 2 ]]
[[ "$(sql "SELECT attempts FROM system_jobs WHERE dedup_key='ci-future'")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key='ci-rollback'")" == 0 ]]
call 200 GET /api/v1/admin/jobs/00000000-0000-0000-0000-000000000021 '' bearer
job_version=$(field data.version)
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert "payload" not in d; assert d["lastErrorCode"]=="UNKNOWN_HANDLER"' "$result"
printf '{"version":%s}\n' "$job_version" > "$work/retry.json"
call 200 POST /api/v1/admin/jobs/00000000-0000-0000-0000-000000000021/retry "$work/retry.json" bearer
call 409 POST /api/v1/admin/jobs/00000000-0000-0000-0000-000000000021/retry "$work/retry.json" bearer
docker rm --force vietroute-auth-ci2 >/dev/null
# Restart không được seed lại quyền CUSTOMER đã bị thu hồi.
docker restart vietroute-auth-ci >/dev/null
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 "$base/health" >/dev/null
[[ "$(sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='CUSTOMER'")" == 0 ]]
call 200 GET /v3/api-docs
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert isinstance(d,dict), "OpenAPI must be a JSON object"; assert "/api/v1/auth/change-password" in d["paths"]; assert "bearerAuth" in d["components"]["securitySchemes"]; assert "CatalogResponse" in d["components"]["schemas"]; assert "201" in d["paths"]["/api/v1/admin/catalogs"]["post"]["responses"]' "$result"

# Tạo DB cũ riêng từ đúng V001 để kiểm tra nâng cấp public -> vietroute, không sửa DB ứng dụng đang test.
printf 'Schema stage: upgrade legacy public data without resetting admin password\n'
docker exec vietroute-db-ci psql -U vietroute_ci -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE vietroute_legacy_ci' >/dev/null
docker exec -i vietroute-db-ci psql -U vietroute_ci -d vietroute_legacy_ci -v ON_ERROR_STOP=1 \
  < src/main/resources/db/migration/V001__auth_and_catalog.sql >/dev/null
legacy_checksum=$(sha256sum src/main/resources/db/migration/V001__auth_and_catalog.sql | cut -d ' ' -f 1)
legacy_hash=$(cat "$work/hash-before")
docker exec -i vietroute-db-ci psql -U vietroute_ci -d vietroute_legacy_ci -v ON_ERROR_STOP=1 >/dev/null <<SQL
CREATE TABLE public.vietroute_schema_history(version VARCHAR(40) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO public.vietroute_schema_history(version,checksum) VALUES ('V001__auth_and_catalog.sql','$legacy_checksum');
INSERT INTO public.auth_roles(code,name_vi,name_en) VALUES ('ADMIN','Quản trị viên','Administrator');
INSERT INTO public.auth_users(username,password_hash,display_name,must_change_password) VALUES ('admin','$legacy_hash','Legacy admin retained',false);
INSERT INTO public.auth_user_roles(user_id,role_code) SELECT id,'ADMIN' FROM public.auth_users WHERE username='admin';
INSERT INTO public.catalog_items(type,code,name_vi,name_en) VALUES ('VEHICLE_TYPE','LEGACY','Danh mục cũ','Legacy catalog');
SQL
start_app vietroute-auth-ci2 18081 vietroute_legacy_ci
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 http://127.0.0.1:18081/health >/dev/null
legacy_sql() { docker exec -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -U vietroute_ci -d vietroute_legacy_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(legacy_sql 'SELECT count(*) FROM vietroute_schema_history')" == 3 ]]
[[ "$(legacy_sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('auth_roles','auth_users','auth_user_roles','catalog_items','vietroute_schema_history')")" == 0 ]]
[[ "$(legacy_sql "SELECT display_name FROM auth_users WHERE id=1 AND username='admin'")" == 'Legacy admin retained' ]]
[[ "$(legacy_sql "SELECT code FROM catalog_items WHERE id=1")" == LEGACY ]]
legacy_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/legacy-hash"
cmp --silent "$work/hash-before" "$work/legacy-hash"
base=http://127.0.0.1:18081
rm -f "$work/cookies"
refresh_csrf
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
[[ "$(field data.user.mustChangePassword)" == False ]]
token=$(field data.accessToken)
call 201 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
[[ "$(field data.id)" -gt 6 ]]
printf 'PostgreSQL bootstrap, JWT, CSRF, roles, catalogs, password change, revocation and account lock checks passed.\n'

printf 'Traffic stage: quota shared across two instances and fail-closed Redis outage\n'
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
docker run --detach --name vietroute-redis-ci --network vietroute-auth-net redis:8-alpine >/dev/null
for attempt in {1..20}; do
  if docker exec vietroute-redis-ci redis-cli ping >/dev/null 2>&1; then break; fi
  sleep 1
done
start_app vietroute-auth-ci 18080 vietroute_ci 3 redis://vietroute-redis-ci:6379
start_app vietroute-auth-ci2 18081 vietroute_ci 3 redis://vietroute-redis-ci:6379
for port in 18080 18081; do
  curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
    --retry-connrefused --max-time 5 "http://127.0.0.1:$port/health" > /dev/null
done
for port in 18080 18081 18080; do
  status=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' "http://127.0.0.1:$port/api/v1/auth/csrf")
  [[ "$status" == 200 ]]
done
status=$(curl --silent --show-error --max-time 10 -D "$work/rate-headers" -o "$result" -w '%{http_code}' \
  -H 'X-Forwarded-For: 192.0.2.222' http://127.0.0.1:18081/api/v1/auth/csrf)
[[ "$status" == 429 ]]
assert_code COM012
grep -qi '^Retry-After:' "$work/rate-headers"
docker stop vietroute-redis-ci >/dev/null
status=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' http://127.0.0.1:18080/api/v1/auth/csrf)
[[ "$status" == 503 ]]
assert_code COM013
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18080/health > /dev/null
printf 'Function permissions, durable workers, lease recovery, shared Redis quota and outage protection passed.\n'
