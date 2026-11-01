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
metrics_token="$(openssl rand -hex 32)"
printf '::add-mask::%s\n' "$db_password" "$admin_password" "$next_password" "$jwt_secret" "$metrics_token"
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
  local database_port="${8:-5432}"
  local database_options="${9-?currentSchema=public}"
  docker run --detach --name "$1" --network vietroute-auth-net --memory 512m \
    -e PORT=10001 -p "127.0.0.1:$2:10001" \
    -e VIETROUTE_ENV=production -e VIETROUTE_COOKIE_SECURE=false -e VIETROUTE_DB_INIT_ENABLED=true \
    -e VIETROUTE_WORKER_ENABLED=false -e VIETROUTE_WORKER_POLL_SECONDS=1 \
    -e VIETROUTE_ACTIVITY_ENABLED=true -e VIETROUTE_ACTIVITY_REQUEST_THRESHOLD=120 \
    -e VIETROUTE_METRICS_TOKEN="$metrics_token" -e VIETROUTE_MONITORING_DB_ENABLED=true -e VIETROUTE_MONITORING_POLL_SECONDS=15 \
    -e VIETROUTE_RATE_REQUESTS="${4:-1000}" -e VIETROUTE_RATE_LOGINS=200 \
    -e VIETROUTE_ACCESS_REGISTRY_CAPACITY=600 \
    -e VIETROUTE_MAIL_ENABLED=false \
    -e VIETROUTE_MAIL_PROVIDER=smtp -e VIETROUTE_MAIL_FROM=noreply@example.invalid \
    -e VIETROUTE_SMTP_HOST=127.0.0.1 -e VIETROUTE_SMTP_PORT=1 -e VIETROUTE_SMTP_USER=ci-only -e VIETROUTE_SMTP_PASSWORD=ci-only \
    -e VIETROUTE_REDIS_URL="${5:-}" \
    -e VIETROUTE_DB_SCHEMA="${6:-vietroute}" \
    -e VIETROUTE_DB_POOL_SIZE="${CI_APPLICATION_POOL_SIZE:-4}" \
    -e VIETROUTE_DB_URL="jdbc:postgresql://vietroute-db-ci:$database_port/$database$database_options" \
    -e VIETROUTE_DB_USER=vietroute_ci -e VIETROUTE_DB_PASSWORD="$db_password" \
    -e VIETROUTE_JWT_SECRET="$jwt_secret" -e VIETROUTE_BOOTSTRAP_ADMIN_USERNAME=admin \
    -e VIETROUTE_BOOTSTRAP_ADMIN_EMAIL=bootstrap-ci@example.invalid \
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
[[ "$(sql 'SELECT count(*) FROM vietroute_schema_history')" == 26 ]]
[[ "$(sql 'SELECT count(*) FROM catalog_items')" == 5 ]]
[[ "$(sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('auth_roles','auth_users','auth_user_roles','catalog_items','vietroute_schema_history')")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM pg_indexes WHERE schemaname='vietroute' AND indexname IN ('idx_auth_users_created_by','idx_auth_users_updated_by','idx_user_roles_role','idx_catalog_created_by','idx_catalog_updated_by')")" == 5 ]]
printf 'Audit stage: reserved actor, non-null seed and database guards\n'
[[ "$(sql "SELECT count(*) FROM auth_users WHERE id=0 AND username='system' AND NOT active AND NOT must_change_password AND created_by=0 AND updated_by=0")" == 1 ]]
[[ "$(sql "SELECT id FROM auth_users WHERE username='admin'")" == 1 ]]
for audit_table in auth_users auth_roles auth_user_roles auth_functions auth_role_functions auth_function_resources auth_account_challenges auth_access_changes catalog_items system_jobs media_assets api_request_journal system_visitor_presence system_activity_buckets; do
  [[ "$(sql "SELECT count(*) FROM $audit_table WHERE created_by IS NULL OR updated_by IS NULL")" == 0 ]]
  [[ "$(sql "SELECT count(*) FROM information_schema.columns WHERE table_schema='vietroute' AND table_name='$audit_table' AND column_name IN ('created_by','updated_by') AND is_nullable='NO'")" == 2 ]]
done
[[ "$(sql 'SELECT count(*) FROM catalog_items WHERE created_by=0 AND updated_by=0')" == 5 ]]
if sql 'UPDATE auth_users SET active=true WHERE id=0' >/dev/null 2>&1; then exit 1; fi
if sql "INSERT INTO auth_user_roles(user_id,role_code) VALUES (0,'ADMIN')" >/dev/null 2>&1; then exit 1; fi
if sql "UPDATE catalog_items SET created_by=NULL WHERE code='SEAT'" >/dev/null 2>&1; then exit 1; fi
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
# Maven verify trước build image chạy toàn bộ security/JPA/PostgreSQL với DB cô lập.
# Giữ từng nhóm drill CI; wrapper kiểm đúng report có đủ ca, không fail/error/skip.
CI_AUTH_PASSWORD="$admin_password" python3 src/test/resources/dev/auth-smoke.py http://127.0.0.1:18080
source "$(dirname "${BASH_SOURCE[0]}")/verify-account.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-managed-accounts.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-foundation.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-access-tree.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-database-guard.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-data-scope.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-access-history.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-access-definitions.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-locations.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-access-registry.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-access-cost.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-business-load.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-admin-recovery.sh"
sql "SELECT md5(password_hash) FROM auth_users WHERE username='admin'" > "$work/bootstrap-hash-before"
docker restart vietroute-auth-ci >/dev/null
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 http://127.0.0.1:18080/health >/dev/null
for port in 18080 18081; do
  CI_AUTH_PASSWORD="$admin_password" python3 src/test/resources/dev/auth-smoke.py "http://127.0.0.1:$port" --after-restart
done
sql "SELECT md5(password_hash) FROM auth_users WHERE username='admin'" > "$work/bootstrap-hash-after"
cmp --silent "$work/bootstrap-hash-before" "$work/bootstrap-hash-after"
[[ "$(sql "SELECT count(*) FROM pg_tables WHERE schemaname='vietroute' AND tablename LIKE 'auth_%'")" == 8 ]]
docker cp vietroute-auth-ci:/usr/local/tomcat/webapps/ROOT.war "$work/auth.war"
mkdir -p "$work/war"
unzip -q "$work/auth.war" -d "$work/war"
account_cp="$work/war/WEB-INF/classes:$work/war/WEB-INF/lib/*"
next_password="$admin_password"
source "$(dirname "${BASH_SOURCE[0]}")/verify-pooler.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-recovery.sh"
printf 'Auth 40 class: đủ nhóm regression, HTTP WAR, restart, pooler và backup/restore CI.\n'
