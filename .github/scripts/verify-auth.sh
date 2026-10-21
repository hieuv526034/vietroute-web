#!/usr/bin/env bash
# Kiểm tra tích hợp bằng PostgreSQL tạm; không đọc secrets hoặc kết nối Neon production.
set -euo pipefail
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
  docker run --detach --name "$1" --network vietroute-auth-net --memory 512m \
    -e PORT=10001 -p "127.0.0.1:$2:10001" \
    -e VIETROUTE_ENV=production -e VIETROUTE_COOKIE_SECURE=false -e VIETROUTE_DB_INIT_ENABLED=true \
    -e VIETROUTE_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci \
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
sql() { docker exec vietroute-db-ci psql -U vietroute_ci -d vietroute_ci -Atc "$1"; }
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='admin'")" == 1 ]]
[[ "$(sql 'SELECT count(*) FROM vietroute_schema_history')" == 1 ]]
[[ "$(sql 'SELECT count(*) FROM catalog_items')" == 5 ]]
docker rm --force vietroute-auth-ci2 >/dev/null
base=http://127.0.0.1:18080
result="$work/response.json"
csrf_token=''
token=''
call() {
  local expected="$1" method="$2" path="$3" payload="${4:-}" mode="${5:-none}"
  local args=(--silent --show-error --max-time 30 --output "$result" --dump-header "$work/headers" --write-out '%{http_code}' -X "$method" -H 'X-Locale: en')
  if [[ "$mode" == cookie* ]]; then args+=(-b "$work/cookies" -c "$work/cookies"); fi
  if [[ "$mode" == cookie ]]; then args+=(-H "X-XSRF-TOKEN: $csrf_token"); fi
  if [[ "$mode" == bearer ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$payload" ]]; then args+=(-H 'Content-Type: application/json' --data-binary "@$payload"); fi
  local actual
  actual=$(curl "${args[@]}" "$base$path")
  if [[ "$actual" != "$expected" ]]; then
    printf 'Unexpected HTTP status: %s %s expected=%s actual=%s\n' "$method" "$path" "$expected" "$actual" >&2
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
call 400 GET '/api/v1/catalogs/VEHICLE_TYPE?size=101'
assert_code COM001
call 400 GET '/api/v1/catalogs/VEHICLE_TYPE?lang=fr'
assert_code COM003
cat > "$work/catalog.json" <<'JSON'
{"type":"VEHICLE_TYPE","code":"CITEST","nameVi":"Danh mục kiểm tra","nameEn":"CI catalog","sortOrder":10}
JSON
call 403 POST /api/v1/admin/catalogs "$work/catalog.json" cookie-no-csrf
assert_code AUTH006
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
call 200 GET /v3/api-docs
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert "/api/v1/auth/change-password" in d["paths"]; assert "bearerAuth" in d["components"]["securitySchemes"]; assert "CatalogResponse" in d["components"]["schemas"]' "$result"
printf 'PostgreSQL bootstrap, JWT, CSRF, roles, catalogs, password change, revocation and account lock checks passed.\n'
