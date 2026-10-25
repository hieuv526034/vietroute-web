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
    -e VIETROUTE_WORKER_ENABLED=true -e VIETROUTE_WORKER_POLL_SECONDS=1 \
    -e VIETROUTE_ACTIVITY_ENABLED=true -e VIETROUTE_ACTIVITY_REQUEST_THRESHOLD=120 \
    -e VIETROUTE_METRICS_TOKEN="$metrics_token" -e VIETROUTE_MONITORING_DB_ENABLED=true -e VIETROUTE_MONITORING_POLL_SECONDS=15 \
    -e VIETROUTE_RATE_REQUESTS="${4:-1000}" -e VIETROUTE_RATE_LOGINS=200 \
    -e VIETROUTE_ACCESS_REGISTRY_CAPACITY=600 \
    -e VIETROUTE_AUTH_SELF_SERVICE_ENABLED="${7:-false}" -e VIETROUTE_MAIL_ENABLED="${7:-false}" \
    -e VIETROUTE_MAIL_PROVIDER=smtp -e VIETROUTE_MAIL_FROM=noreply@example.invalid \
    -e VIETROUTE_SMTP_HOST=127.0.0.1 -e VIETROUTE_SMTP_PORT=1 -e VIETROUTE_SMTP_USER=ci-only -e VIETROUTE_SMTP_PASSWORD=ci-only \
    -e VIETROUTE_REDIS_URL="${5:-}" \
    -e VIETROUTE_DB_SCHEMA="${6:-vietroute}" \
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
[[ "$(sql 'SELECT count(*) FROM vietroute_schema_history')" == 19 ]]
[[ "$(sql 'SELECT count(*) FROM catalog_items')" == 5 ]]
[[ "$(sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('auth_roles','auth_users','auth_user_roles','catalog_items','vietroute_schema_history')")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM pg_indexes WHERE schemaname='vietroute' AND indexname IN ('idx_auth_users_created_by','idx_auth_users_updated_by','idx_auth_user_roles_role_code','idx_catalog_created_by','idx_catalog_updated_by')")" == 5 ]]
printf 'Audit stage: reserved actor, non-null seed and database guards\n'
[[ "$(sql "SELECT count(*) FROM auth_users WHERE id=0 AND username='system' AND NOT active AND NOT must_change_password AND created_by=0 AND updated_by=0")" == 1 ]]
[[ "$(sql "SELECT id FROM auth_users WHERE username='admin'")" == 1 ]]
for audit_table in auth_users auth_roles auth_permissions auth_user_roles auth_role_permissions catalog_items system_jobs media_assets api_request_journal auth_access_policies auth_access_resources auth_password_history auth_account_challenges system_visitor_presence system_activity_buckets auth_access_changes auth_data_scopes auth_scope_memberships auth_scope_roles; do
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
refresh_csrf
login_body system 'Not-A-System-Password-2026!'
call 401 POST /api/v1/auth/login "$work/login.json" cookie
assert_code AUTH001
[[ "$(sql 'SELECT failed_login_attempts FROM auth_users WHERE id=0')" == 0 ]]
printf 'Auth stage: initial login, cookies and public catalog\n'
login_body admin "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
initial_token="$token"
login_trace=$(field traceId)
grep -qi 'HttpOnly' "$work/headers"
grep -qi 'SameSite=Lax' "$work/headers"
[[ "$(field data.user.mustChangePassword)" == False ]]
[[ "$(field data.user.emailVerificationRequired)" == False ]]
[[ "$(field data.user.email)" == bootstrap-ci@example.invalid ]]
call 200 GET /api/v1/admin/system/runtime '' bearer
call 200 GET /admin '' cookie
call 200 GET '/account?lang=en' '' cookie
grep -Fq 'href="/admin?lang=en"' "$result"
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
call 200 GET /api/v1/auth/me '' cookie
[[ "$(field data.username)" == admin ]]
# Document mới dùng cookie sau đổi mật khẩu phải dựng đủ menu từ quyền DB, không giữ header phiên tạm.
call 200 GET '/account?lang=en&passwordChanged=true' '' cookie
for page_path in /admin /media /monitoring /operations/jobs /operations/access /operations/accounts /operations/configuration /operations/api-journal; do
  grep -Fq "href=\"$page_path?lang=en\"" "$result"
done
grep -q 'data-password-changed=' "$result"
printf 'Navigation stage: authenticated home enters workspace and login redirects without another password\n'
call 200 GET '/?lang=vi' '' cookie
python3 -c 'import re,sys; h=open(sys.argv[1]).read(); a=re.search(r"id=\"home-workspace\"[\s\S]*?</a>",h).group(); assert "href=\"/admin?lang=vi\"" in a' "$result"
call 302 GET '/login?lang=en' '' cookie
grep -qi '^Location: .*/admin?lang=en' "$work/headers"
call 200 GET /api/v1/auth/me '' bearer
call 200 GET /api/v1/admin/system/runtime '' bearer
[[ "$(field data.databaseProbeEnabled)" == True ]]
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["heapUsedBytes"]>0 and len(d["instanceId"])==36' "$result"
printf 'System management stage: protected pages, safe config and account lookup\n'
for page_path in jobs access configuration api-journal; do
  for locale in vi en; do
    call 200 GET "/operations/$page_path?lang=$locale" '' cookie
    if [[ "$page_path" == api-journal ]]; then
      grep -q 'journal-dialog' "$result"
      grep -q 'journal-export-tab' "$result"
    else
      grep -q 'data-operations=' "$result"
      grep -q 'ops-controls' "$result"
    fi
    if grep -q '??ui.ops' "$result"; then exit 1; fi
  done
done
for locale in vi en; do
  call 200 GET "/admin?lang=$locale" '' cookie
  grep -q 'dashboard-links' "$result"
  grep -q 'aria-current="page"' "$result"
  if grep -q 'catalog-workspace' "$result"; then exit 1; fi
  for catalog_path in vehicle-types booking-sources; do
    call 200 GET "/catalogs/$catalog_path?lang=$locale" '' cookie
    grep -q 'catalog-workspace' "$result"
    grep -q 'catalog-previous' "$result"
    grep -q 'catalog-dialog' "$result"
    grep -q 'catalog-search' "$result"
    grep -q '/assets/js/workspace.js' "$result"
    grep -q '/assets/js/catalog.js' "$result"
    grep -q 'aria-current="page"' "$result"
    if grep -q 'overview-panel\|??ui\.' "$result"; then exit 1; fi
  done
  for catalog_path in provinces locations; do
    call 200 GET "/catalogs/$catalog_path?lang=$locale" '' cookie
    grep -q 'location-workspace' "$result"
    grep -q "data-section=\"$catalog_path\"" "$result"
    if grep -q '??ui\.' "$result"; then exit 1; fi
  done
  for fleet_path in carriers vehicles drivers; do
    call 200 GET "/operations/$fleet_path?lang=$locale" '' cookie
    grep -q 'fleet-workspace' "$result"
    grep -q "data-kind=\"$fleet_path\"" "$result"
    grep -q 'fleet-dialog' "$result"
    grep -q 'fleet-previous' "$result"
    if grep -q '??ui\.' "$result"; then exit 1; fi
  done
  call 200 GET "/operations/demo?lang=$locale" '' cookie
  grep -q 'demo-create' "$result"
  if grep -q '??ui\.' "$result"; then exit 1; fi
  [[ "$(sql 'SELECT count(*) FROM carriers')" == 0 ]]
  call 200 GET "/media?lang=$locale" '' cookie
  grep -q 'data-enabled="false"' "$result"
  grep -q 'media-dialog' "$result"
  if grep -q '??ui\.' "$result"; then exit 1; fi
  call 200 GET "/account?lang=$locale" '' cookie
  grep -q 'password-dialog' "$result"
  if grep -q '??ui\.' "$result"; then exit 1; fi
done
call 200 GET /api/v1/admin/system/configuration '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["readOnly"] is True; assert set(d["database"])=={"maximumPoolSize","acquisitionTimeoutMs","connectTimeoutSeconds","socketTimeoutSeconds"}; assert d["database"]["maximumPoolSize"]==4; assert d["worker"]["enabled"] is True; assert not any(k in json.dumps(d).lower() for k in ["password","refreshtoken","clientsecret","jdbc:postgresql","jwtkey"])' "$result"
call 200 GET '/api/v1/admin/access/users?username=Ad&page=0&size=1' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["totalElements"]==1; u=d["content"][0]; assert u["username"]=="admin" and u["roles"]==["ADMIN"]; assert set(u)=={"id","username","displayName","active","roles","version","createdAt","updatedAt"}' "$result"
call 400 GET '/api/v1/admin/access/users?username=%25' '' bearer
assert_code COM001
call 200 GET '/api/v1/admin/access/users?username=NoSuchUser&page=0&size=20' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
call 200 GET '/api/v1/admin/access/users?username=system' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
printf '{"roles":["ADMIN"],"version":0}\n' > "$work/system-roles.json"
call 403 PUT /api/v1/admin/access/users/0/roles "$work/system-roles.json" bearer
assert_code AUTH003
call 200 GET '/api/v1/admin/jobs?status=DEAD&page=0&size=1' '' bearer
call 400 GET '/api/v1/admin/jobs?status=NOT_VALID' '' bearer
assert_code COM002
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
journal_trace=$(field traceId)
journal_retained_trace="$journal_trace"
printf 'API journal stage: trace lookup, controller, filtered bodies and auth omission\n'
call 200 GET "/api/v1/admin/system/api-journal?traceId=$journal_trace" '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["capacity"]==10000 and d["retentionMinutes"]==10080 and d["storage"]=="POSTGRESQL"; r=d["records"]; assert r["totalElements"]==1; e=r["content"][0]; assert e["userId"]==1 and e["username"]=="admin"; assert e["status"]==400 and e["code"]=="COM001" and "CatalogController#create" in e["controller"]' "$result"
# field() dùng key object; chỉ số mảng được đọc rõ ràng, không log snapshot chứa dữ liệu test.
journal_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["records"]["content"][0]["id"])' "$result")
call 200 GET "/api/v1/admin/system/api-journal/$journal_id" '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; body=json.loads(d["requestBody"]); response=json.loads(d["responseBody"]); assert body["code"]=="[REDACTED]" and body["nameVi"]=="[REDACTED]" and body["type"]=="VEHICLE_TYPE"; assert response["code"]=="COM001" and "message" not in response; assert d["replayPath"]=="/api/v1/admin/catalogs"; assert "bad-code!" not in json.dumps(d)' "$result"
call 200 GET "/api/v1/admin/system/api-journal?traceId=$login_trace" '' bearer
[[ "$(field data.records.totalElements)" == 1 ]]
journal_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["records"]["content"][0]["id"])' "$result")
call 200 GET "/api/v1/admin/system/api-journal/$journal_id" '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert "capture" in json.loads(d["requestBody"]); assert json.loads(d["responseBody"])["data"]=="OMITTED_BY_POLICY"; assert "accessToken" not in d["responseBody"]; assert "password" not in d["requestBody"]' "$result"
call 200 GET "/api/v1/admin/system/api-journal?endpoint=api-journal" '' bearer
[[ "$(field data.records.totalElements)" == 0 ]]
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); del d["code"]; json.dump(d,sys.stdout)' "$work/catalog.json" > "$work/generated.json"
call 201 POST /api/v1/admin/catalogs "$work/generated.json" bearer
python3 -c 'import json,re,sys; d=json.load(open(sys.argv[1])); assert re.fullmatch(r"[A-Z][A-Z0-9]{2,39}",d["data"]["code"])' "$result"
call 200 POST /api/v1/auth/logout '' cookie
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
# Restart giữ nguyên hash đã đổi; không lấy mật khẩu bootstrap ghi đè lên tài khoản có sẵn.
sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/hash-before"
printf 'Auth stage: restart does not reset password\n'
sql "INSERT INTO auth_access_resources(kind,target,handler,description,public_route,configurable) VALUES ('ENDPOINT','GET /retained-version-route','LegacyController#read','CI rolling deployment fixture',false,false)" >/dev/null
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
# Lịch sử đã commit tồn tại qua restart, không phụ thuộc JVM/instance cũ.
call 200 GET "/api/v1/admin/system/api-journal?traceId=$journal_retained_trace" '' cookie
[[ "$(field data.records.totalElements)" == 1 ]]
call 200 POST /api/v1/auth/logout '' cookie
# Tạo user CUSTOMER trong DB test riêng để kiểm tra quyền, khóa user và bộ đếm sai phải được commit.
sql "INSERT INTO auth_users(username,password_hash,display_name,email_verified_at,must_change_password) SELECT 'customer',password_hash,'CI customer',CURRENT_TIMESTAMP,false FROM auth_users WHERE username='admin'" >/dev/null
printf 'Auth stage: role checks and account lock\n'
sql "INSERT INTO auth_user_roles(user_id,role_code) SELECT id,'CUSTOMER' FROM auth_users WHERE username='customer'" >/dev/null
login_body customer "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 GET '/?lang=en' '' cookie
python3 -c 'import re,sys; h=open(sys.argv[1]).read(); a=re.search(r"id=\"home-workspace\"[\s\S]*?</a>",h).group(); assert "href=\"/account?lang=en\"" in a' "$result"
call 302 GET '/login?lang=en' '' cookie
grep -qi '^Location: .*/account?lang=en' "$work/headers"
call 403 GET /admin '' cookie
call 403 GET /api/v1/admin/system/api-journal '' bearer
for protected_page in api-journal configuration jobs access; do
  call 403 GET "/operations/$protected_page" '' cookie
done
call 403 GET /monitoring '' cookie
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
printf 'Media stage: migration, owner scope, multipart limits and disabled provider\n'
media_id=$(sql "INSERT INTO media_assets(public_id,status,url,thumbnail_url,format,bytes,width,height,created_by,updated_by) VALUES ('ci-public-image','READY','https://res.cloudinary.com/demo/image/upload/sample.png','https://res.cloudinary.com/demo/image/upload/sample.png','png',128,64,32,1,1) RETURNING id" | sed -n '1p')
call 200 GET "/api/v1/media/$media_id" '' bearer
[[ "$(field data.createdBy)" == 1 ]]
call 200 GET /media '' cookie
# Trang HTML kiểm bằng curl riêng vì helper field chỉ dùng JSON response.
call 200 GET /v3/api-docs '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert "multipart/form-data" in d["paths"]["/api/v1/media"]["post"]["requestBody"]["content"]' "$result"
token="$customer_token"
call 403 GET /api/v1/media '' bearer
sql "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('CUSTOMER','MEDIA_READ')" >/dev/null
call 200 GET /api/v1/media '' bearer
[[ "$(field data.totalElements)" == 0 ]]
call 404 GET "/api/v1/media/$media_id" '' bearer
sql "DELETE FROM auth_role_permissions WHERE role_code='CUSTOMER' AND permission_code='MEDIA_READ'" >/dev/null
token="$admin_token"
printf '%s\n' '{"version":0}' > "$work/media-delete.json"
call 503 DELETE "/api/v1/media/$media_id" "$work/media-delete.json" bearer
assert_code MEDIA001
python3 -c 'import sys; open(sys.argv[1],"wb").write(b"X"*(5242880+1))' "$work/large-image.bin"
actual=$(curl --silent --show-error --max-time 30 -o "$result" -w '%{http_code}' \
  -H "Authorization: Bearer $token" -F "file=@$work/large-image.bin;type=image/png" "$base/api/v1/media")
[[ "$actual" == 413 ]]
assert_code COM014
actual=$(curl --silent --show-error --max-time 30 -o "$result" -w '%{http_code}' \
  -H "Authorization: Bearer $token" -H 'Transfer-Encoding: chunked' \
  -F "file=@$work/large-image.bin;type=image/png" "$base/api/v1/media")
[[ "$actual" == 413 ]]
assert_code COM014
printf 'not-a-real-image' > "$work/tiny-image.png"
actual=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' \
  -H "Authorization: Bearer $token" -F "file=@$work/tiny-image.png;type=image/png" "$base/api/v1/media")
[[ "$actual" == 503 ]]
assert_code MEDIA001
# Job dọn upload cũ phải giữ nguyên READY và không gọi Cloudinary thật.
sql "INSERT INTO system_jobs(id,type,dedup_key,payload,created_by) VALUES (gen_random_uuid(),'MEDIA_CLEANUP','ci-media-ready',jsonb_build_object('id',$media_id),1)" >/dev/null
for attempt in {1..15}; do
  [[ "$(sql "SELECT status FROM system_jobs WHERE dedup_key='ci-media-ready'")" == SUCCEEDED ]] && break
  sleep 1
done
[[ "$(sql "SELECT status FROM system_jobs WHERE dedup_key='ci-media-ready'")" == SUCCEEDED ]]
[[ "$(sql "SELECT status FROM media_assets WHERE id=$media_id")" == READY ]]
call 200 GET /api/v1/admin/access/permissions '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert any(p["code"]=="CATALOG_WRITE" for p in d["data"])' "$result"
printf '%s\n' '{"permissions":["CATALOG_CREATE"],"version":0}' > "$work/permissions.json"
call 200 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/permissions.json" bearer
[[ "$(field data.version)" == 1 ]]
call 409 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/permissions.json" bearer
assert_code COM008
call 403 PUT /api/v1/admin/access/roles/ADMIN/permissions "$work/permissions.json" bearer
token="$customer_token"
sed 's/CITEST/PERMALLOW/' "$work/catalog.json" > "$work/permission-catalog.json"
call 201 POST /api/v1/admin/catalogs "$work/permission-catalog.json" bearer
catalog_id=$(field data.id)
printf '%s\n' '{"nameVi":"Tên đã sửa","nameEn":"Updated name","sortOrder":12,"version":0}' > "$work/update-catalog.json"
printf '%s\n' '{"version":0}' > "$work/delete-catalog.json"
call 403 GET "/api/v1/admin/catalogs/$catalog_id" '' bearer
call 403 PUT "/api/v1/admin/catalogs/$catalog_id" "$work/update-catalog.json" bearer
call 403 DELETE "/api/v1/admin/catalogs/$catalog_id" "$work/delete-catalog.json" bearer
call 403 GET /api/v1/admin/jobs '' bearer
call 403 GET /api/v1/admin/access/users '' bearer
call 403 GET /api/v1/admin/system/configuration '' bearer
token="$admin_token"
printf '%s\n' '{"permissions":["CATALOG_WRITE"],"version":1}' > "$work/legacy-write.json"
call 200 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/legacy-write.json" bearer
token="$customer_token"
call 200 PUT "/api/v1/admin/catalogs/$catalog_id" "$work/update-catalog.json" bearer
[[ "$(field data.version)" == 1 ]]
[[ "$(field data.updatedBy)" != 1 ]]
call 409 PUT "/api/v1/admin/catalogs/$catalog_id" "$work/update-catalog.json" bearer
assert_code COM008
call 403 DELETE "/api/v1/admin/catalogs/$catalog_id" "$work/delete-catalog.json" bearer
token="$admin_token"
printf '%s\n' '{"permissions":["CATALOG_READ","CATALOG_UPDATE","CATALOG_DELETE"],"version":2}' > "$work/crud.json"
call 200 PUT /api/v1/admin/access/roles/CUSTOMER/permissions "$work/crud.json" bearer
token="$customer_token"
call 200 GET "/api/v1/admin/catalogs/$catalog_id" '' bearer
printf '%s\n' '{"version":1}' > "$work/delete-catalog.json"
call 200 DELETE "/api/v1/admin/catalogs/$catalog_id" "$work/delete-catalog.json" bearer
[[ "$(field data.active)" == False ]]
[[ "$(field data.version)" == 2 ]]
call 409 DELETE "/api/v1/admin/catalogs/$catalog_id" "$work/delete-catalog.json" bearer
call 200 GET '/api/v1/catalogs/VEHICLE_TYPE' '' none
python3 -c 'import json,sys; assert not any(row["code"]=="PERMALLOW" for row in json.load(open(sys.argv[1]))["data"]["content"])' "$result"
token="$admin_token"
printf '%s\n' '{"permissions":[],"version":3}' > "$work/revoke.json"
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
printf 'Resource discovery stage: persisted UI/API inventory, public classification and paging\n'
[[ "$(sql 'SELECT count(*) FROM auth_access_resources')" -ge 20 ]]
[[ "$(sql "SELECT count(*) FROM auth_access_resources WHERE target='GET /retained-version-route'")" == 1 ]]
[[ "$(sql 'SELECT count(*) FROM auth_access_resources WHERE created_by<>0 OR updated_by<>0')" == 0 ]]
[[ "$(sql "SELECT count(*) FROM auth_access_resources WHERE kind='MENU' AND target='GET /media'")" == 1 ]]
[[ "$(sql "SELECT count(*) FROM auth_access_resources WHERE kind='ENDPOINT' AND target='GET /api/v1/catalogs/{type}' AND public_route AND NOT configurable")" == 1 ]]
[[ "$(sql 'SELECT count(*) FROM auth_access_resources WHERE public_route AND configurable')" == 0 ]]
call 200 GET '/api/v1/admin/access/resources?page=0&size=10' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["size"]==10 and len(d["content"])==10 and d["totalElements"]>=20; assert all(x["firstSeenAt"] and x["lastSeenAt"] for x in d["content"])' "$result"
call 400 GET '/api/v1/admin/access/resources?size=101' '' bearer
saved_admin_token="$token"
# Role vừa thay đổi đã thu hồi customer_token ở ca trước; dùng token mới để kiểm 403 thay vì 401.
refresh_csrf
login_body customer "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
customer_token=$(field data.accessToken)
token="$customer_token"
call 403 GET '/api/v1/admin/access/resources' '' bearer
token="$saved_admin_token"
printf 'Foundation registry stage: dynamic roles, policy guards and live DB revocation\n'
call 200 GET /api/v1/admin/access/targets '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert any(x["kind"]=="MENU" and x["target"]=="GET /media" for x in d); assert not any("/auth" in x["target"] or "/admin/access" in x["target"] for x in d)' "$result"
printf '%s\n' '{"nameVi":"Hỗ trợ","nameEn":"Support","active":true,"version":0}' > "$work/registry.json"
call 201 POST /api/v1/admin/access/registry/roles/SUPPORT "$work/registry.json" bearer
call 409 POST /api/v1/admin/access/registry/roles/SUPPORT "$work/registry.json" bearer
call 201 POST /api/v1/admin/access/registry/permissions/SUPPORT_READ "$work/registry.json" bearer
call 400 POST /api/v1/admin/access/registry/permissions/ROLE_READ "$work/registry.json" bearer
call 400 POST /api/v1/admin/access/registry/permissions/SUPPORT_WRITE "$work/registry.json" bearer
call 200 GET /api/v1/auth/me '' bearer
python3 -c 'import json,sys; assert "SUPPORT_READ" in json.load(open(sys.argv[1]))["data"]["permissions"]' "$result"
printf '%s\n' '{"permissions":["MEDIA_VIEW","MEDIA_READ","MEDIA_UPDATE","SUPPORT_READ"],"version":0}' > "$work/support-permissions.json"
call 200 PUT /api/v1/admin/access/roles/SUPPORT/permissions "$work/support-permissions.json" bearer
call 200 GET '/api/v1/admin/access/users?username=customer' '' bearer
customer_version=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["content"][0]["version"])' "$result")
printf '{"roles":["SUPPORT"],"version":%s}\n' "$customer_version" > "$work/support-role.json"
call 200 PUT "/api/v1/admin/access/users/$customer_id/roles" "$work/support-role.json" bearer
login_body customer "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
support_token=$(field data.accessToken)
token="$support_token"
call 200 GET /media '' bearer
call 403 GET /api/v1/admin/access/registry/roles '' bearer
printf '%s\n' '{"title":"Xe thử nghiệm","altText":"Ảnh kiểm CI","version":0}' > "$work/media-update.json"
call 404 PUT "/api/v1/media/$media_id" "$work/media-update.json" bearer
token="$admin_token"
call 200 PUT "/api/v1/media/$media_id" "$work/media-update.json" bearer
[[ "$(field data.title)" == 'Xe thử nghiệm' ]]
call 409 PUT "/api/v1/media/$media_id" "$work/media-update.json" bearer
printf '%s\n' '{"kind":"MENU","target":"GET /media","nameVi":"Ảnh","nameEn":"Media","enabled":false,"permissions":[],"roles":[],"version":0}' > "$work/policy.json"
call 201 POST /api/v1/admin/access/policies "$work/policy.json" bearer
policy_id=$(python3 -c 'import json,sys; print(next(x["id"] for x in json.load(open(sys.argv[1]))["data"] if x["target"]=="GET /media"))' "$result")
token="$support_token"
call 403 GET /media '' bearer
call 200 GET '/?lang=vi' '' bearer
if grep -q 'href="/media?lang=vi"' "$result"; then exit 1; fi
token="$admin_token"
call 200 GET /media '' bearer
call 200 DELETE "/api/v1/admin/access/policies/$policy_id?version=0" '' bearer
call 409 DELETE "/api/v1/admin/access/policies/$policy_id?version=0" '' bearer
printf '%s\n' '{"kind":"ENDPOINT","target":"GET /api/v1/media/{id}","nameVi":"Đọc ảnh","nameEn":"Read image","enabled":false,"permissions":[],"roles":[],"version":0}' > "$work/endpoint-policy.json"
call 201 POST /api/v1/admin/access/policies "$work/endpoint-policy.json" bearer
policy_id=$(python3 -c 'import json,sys; print(next(x["id"] for x in json.load(open(sys.argv[1]))["data"] if x["kind"]=="ENDPOINT"))' "$result")
token="$support_token"
call 403 GET "/api/v1/media/$media_id" '' bearer
token="$admin_token"
call 200 DELETE "/api/v1/admin/access/policies/$policy_id?version=0" '' bearer
printf '%s\n' '{"nameVi":"Hỗ trợ","nameEn":"Support","active":false,"version":0}' > "$work/disable-registry.json"
call 200 PUT /api/v1/admin/access/registry/permissions/SUPPORT_READ "$work/disable-registry.json" bearer
call 409 PUT /api/v1/admin/access/registry/permissions/SUPPORT_READ "$work/disable-registry.json" bearer
token="$support_token"
call 200 GET /api/v1/auth/me '' bearer
python3 -c 'import json,sys; assert "SUPPORT_READ" not in json.load(open(sys.argv[1]))["data"]["permissions"]' "$result"
token="$admin_token"
call 200 PUT /api/v1/admin/access/registry/roles/SUPPORT "$work/disable-registry.json" bearer
token="$support_token"
call 403 GET /media '' bearer
token="$admin_token"
printf 'Activity stage: shared DB online, rejection counters, retention and privacy\n'
# Tạo tín hiệu bằng hàm DB trên PostgreSQL tạm, không tạo tải giả trên Neon.
sql "SELECT record_system_activity(repeat('a',64),repeat('b',64),NULL,429) FROM generate_series(1,12)" >/dev/null
call 200 GET /api/v1/admin/system/activity '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["enabled"] and d["onlineBrowsers"]>0 and d["onlineUsers"]>0 and d["requests60Minutes"]>0 and d["limited60Minutes"]>=12' "$result"
call 200 GET '/api/v1/admin/system/activity/online?page=0&size=1' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["size"]==1 and len(d["content"])==1; assert set(d["content"][0])=={"userId","username","visitor","firstSeen","lastSeen"}' "$result"
call 200 GET /api/v1/admin/system/activity/anomalies '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert any(x["limited"]>=12 for x in d)' "$result"
token="$support_token"
call 403 GET /api/v1/admin/system/activity '' bearer
token="$admin_token"
[[ "$(sql "SELECT count(*) FROM system_visitor_presence WHERE visitor_hash ~ '^[a-f0-9]{64}$'")" -gt 0 ]]
[[ "$(sql 'SELECT count(*) FROM system_visitor_presence')" -le 10000 ]]
[[ "$(sql 'SELECT count(*) FROM system_activity_buckets')" -le 10000 ]]
call 200 GET /api/v1/admin/system/status '' bearer
[[ "$(field data.workerRunning)" == True ]]
printf '%s\n' '{"to":"nobody@example.invalid","subject":"CI only","text":"No real mail is sent"}' > "$work/mail.json"
# Mail tắt trong CI: không gọi Google/SMTP hoặc gửi đến người dùng thật.
actual=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -H 'Idempotency-Key: test-mail-key' \
  --data-binary "@$work/mail.json" "$base/api/v1/admin/mail")
[[ "$actual" == 503 ]]
assert_code MAIL001

# Chèn fixture hết hạn chỉ trong DB CI; handler bảo trì phải dọn DB, không tạo cache RAM.
sql "INSERT INTO api_request_journal(id,recorded_at,started_at,instance_id,trace_id,method,endpoint,controller,http_status,duration_ms,code,message_key,diagnostic,sql_state,replay_path,query_json,request_body,response_body,content_kind) VALUES (gen_random_uuid(),CURRENT_TIMESTAMP-INTERVAL '8 days',CURRENT_TIMESTAMP-INTERVAL '8 days','ci-node','ci-expired-journal','GET','/api/fixture','Fixture',200,1,'OK000','success.ok','SUCCESS','','/api/fixture','{}','{}','{}','JSON')" >/dev/null
# Fixture vượt mục tiêu: PostgreSQL tạm kiểm dọn theo dung lượng, không chèn dữ liệu vào Neon.
sql "INSERT INTO api_request_journal(id,recorded_at,started_at,instance_id,trace_id,method,endpoint,controller,http_status,duration_ms,code,message_key,diagnostic,sql_state,replay_path,query_json,request_body,response_body,content_kind) SELECT gen_random_uuid(),CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP-INTERVAL '1 day','ci-node','ci-capacity-journal','GET','/api/fixture','Fixture',200,1,'OK000','success.ok','SUCCESS','','/api/fixture','{}','{}','{}','JSON' FROM generate_series(1,10025)" >/dev/null
call 200 GET '/api/v1/admin/system/api-journal?traceId=ci-expired-journal' '' bearer
[[ "$(field data.records.totalElements)" == 0 ]]
printf 'Worker stage: concurrent claims, lease recovery, dead jobs and retry\n'
start_app vietroute-auth-ci2 18081
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 http://127.0.0.1:18081/health > /dev/null
saved_base="$base"
base=http://127.0.0.1:18081
call 200 GET /api/v1/admin/system/activity '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["requests60Minutes"]>0 and d["limited60Minutes"]>=12' "$result"
call 200 GET "/api/v1/admin/system/api-journal?traceId=$journal_retained_trace" '' bearer
[[ "$(field data.records.totalElements)" == 1 ]]
base="$saved_base"
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) SELECT ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'SYSTEM_CLEANUP','ci-cleanup-'||n,'{}' FROM generate_series(1,12) n" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000001','SYSTEM_CLEANUP','ci-cleanup-1','{}') ON CONFLICT DO NOTHING" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload,status,attempts,locked_until,lease_token) VALUES ('00000000-0000-0000-0000-000000000020','SYSTEM_CLEANUP','ci-expired','{}','RUNNING',1,CURRENT_TIMESTAMP-INTERVAL '1 minute','00000000-0000-0000-0000-000000000099')" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000021','UNKNOWN_TEST','ci-dead','{}')" >/dev/null
sql "INSERT INTO system_jobs(id,type,dedup_key,payload,available_at) VALUES ('00000000-0000-0000-0000-000000000022','SYSTEM_CLEANUP','ci-future','{}',CURRENT_TIMESTAMP+INTERVAL '1 hour')" >/dev/null
sql "BEGIN; INSERT INTO system_jobs(id,type,dedup_key,payload) VALUES ('00000000-0000-0000-0000-000000000023','SYSTEM_CLEANUP','ci-rollback','{}'); ROLLBACK" >/dev/null
for attempt in {1..40}; do
  if [[ "$(sql "SELECT count(*) FROM system_jobs WHERE status='SUCCEEDED' AND (dedup_key LIKE 'ci-cleanup-%' OR dedup_key='ci-expired')")" == 13 && "$(sql "SELECT status FROM system_jobs WHERE dedup_key='ci-dead'")" == DEAD ]]; then break; fi
  sleep 1
done
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE status='SUCCEEDED' AND (dedup_key LIKE 'ci-cleanup-%' OR dedup_key='ci-expired')")" == 13 ]]
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key LIKE 'ci-cleanup-%' AND attempts=1 AND payload='{}'::jsonb")" == 12 ]]
[[ "$(sql "SELECT attempts FROM system_jobs WHERE dedup_key='ci-expired'")" == 2 ]]
[[ "$(sql "SELECT attempts FROM system_jobs WHERE dedup_key='ci-future'")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM api_request_journal WHERE trace_id='ci-expired-journal'")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM api_request_journal WHERE trace_id='ci-capacity-journal'")" -lt 10025 ]]
[[ "$(sql 'SELECT count(*) FROM api_request_journal')" -le 10010 ]]
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key='ci-rollback'")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key LIKE 'ci-cleanup-%' AND created_by=0 AND updated_by=0")" == 12 ]]
[[ "$(sql 'SELECT count(*) FROM api_request_journal WHERE created_by<>0 OR updated_by<>0')" == 0 ]]
call 200 GET '/api/v1/admin/jobs?status=DEAD&size=100' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["totalElements"]>=1 and all(j["status"]=="DEAD" for j in d["content"]); assert all("payload" not in j and "dedupKey" not in j for j in d["content"])' "$result"
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
UPDATE public.auth_users SET created_by=1,updated_by=NULL WHERE username='admin';
UPDATE public.catalog_items SET updated_by=1,created_at='2020-01-01T00:00:00Z',updated_at='2020-01-02T00:00:00Z',version=7 WHERE code='LEGACY';
SQL
start_app vietroute-auth-ci2 18081 vietroute_legacy_ci 300 '' legacy_custom
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 http://127.0.0.1:18081/health >/dev/null
legacy_sql() { docker exec -e PGOPTIONS=-csearch_path=legacy_custom vietroute-db-ci psql -U vietroute_ci -d vietroute_legacy_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(legacy_sql 'SELECT count(*) FROM vietroute_schema_history')" == 19 ]]
[[ "$(legacy_sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('auth_roles','auth_users','auth_user_roles','catalog_items','vietroute_schema_history')")" == 0 ]]
[[ "$(legacy_sql "SELECT display_name FROM auth_users WHERE id=1 AND username='admin'")" == 'Legacy admin retained' ]]
[[ "$(legacy_sql "SELECT code FROM catalog_items WHERE id=1")" == LEGACY ]]
[[ "$(legacy_sql "SELECT created_by=1 AND updated_by=0 FROM auth_users WHERE id=1")" == t ]]
[[ "$(legacy_sql "SELECT created_by=0 AND updated_by=1 AND created_at='2020-01-01T00:00:00Z' AND updated_at='2020-01-02T00:00:00Z' AND version=7 FROM catalog_items WHERE id=1")" == t ]]
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

printf 'Schema stage: custom schema, wrong-schema guard and offline rename preserve data/checksums\n'
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
docker exec vietroute-db-ci psql -U vietroute_ci -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE vietroute_schema_ci' >/dev/null
# Tên trùng keyword vẫn chạy được: identifier schema luôn được quote sau whitelist.
start_app vietroute-auth-ci 18080 vietroute_schema_ci 300 '' select
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 http://127.0.0.1:18080/health >/dev/null
custom_sql() { docker exec -e 'PGOPTIONS=-csearch_path="select"' vietroute-db-ci psql -U vietroute_ci -d vietroute_schema_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(custom_sql 'SELECT count(*) FROM vietroute_schema_history')" == 19 ]]
[[ "$(custom_sql 'SELECT count(*) FROM catalog_items')" == 5 ]]
[[ "$(custom_sql "SELECT count(*) FROM pg_namespace WHERE nspname='vietroute'")" == 0 ]]
for migration in src/main/resources/db/migration/V00*.sql; do
  version=$(basename "$migration")
  checksum=$(sha256sum "$migration" | cut -d ' ' -f 1)
  [[ "$(custom_sql "SELECT checksum FROM vietroute_schema_history WHERE version='$version'")" == "$checksum" ]]
done
custom_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/custom-hash"
# URL cố tình currentSchema=public; bảng public cùng tên không được che schema cấu hình.
custom_sql 'CREATE TABLE public.catalog_items(id BIGINT)' >/dev/null
base=http://127.0.0.1:18080
rm -f "$work/cookies"
refresh_csrf
login_body admin "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 POST /api/v1/auth/change-password "$work/password.json" bearer
token=$(field data.accessToken)
custom_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/custom-hash"
login_body admin "$next_password"
call 200 GET /api/v1/catalogs/VEHICLE_TYPE
[[ "$(field data.totalElements)" == 3 ]]
call 201 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
custom_catalog_id=$(field data.id)
call 200 GET "/api/v1/admin/catalogs/$custom_catalog_id" '' bearer
call 200 GET /api/v1/admin/system/status '' bearer
start_app vietroute-auth-ci2 18081 vietroute_schema_ci 300 '' wrong_schema
for attempt in {1..30}; do
  docker logs vietroute-auth-ci2 > "$work/schema-guard.log" 2>&1
  if grep -q 'Application history is in another schema' "$work/schema-guard.log"; then break; fi
  sleep 1
done
grep -q 'Application history is in another schema' "$work/schema-guard.log"
[[ "$(custom_sql "SELECT count(*) FROM pg_namespace WHERE nspname='wrong_schema'")" == 0 ]]
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
# Rename chỉ chạy trong DB CI khi mọi instance đã dừng, không tự đổi schema của app đang phục vụ.
custom_sql 'ALTER SCHEMA "select" RENAME TO renamed_app' >/dev/null
start_app vietroute-auth-ci 18080 vietroute_schema_ci 300 '' renamed_app
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
  --retry-connrefused --max-time 5 "$base/health" >/dev/null
renamed_sql() { docker exec -e PGOPTIONS=-csearch_path=renamed_app vietroute-db-ci psql -U vietroute_ci -d vietroute_schema_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
[[ "$(renamed_sql 'SELECT count(*) FROM vietroute_schema_history')" == 19 ]]
[[ "$(renamed_sql 'SELECT count(*) FROM catalog_items')" == 6 ]]
renamed_sql "SELECT password_hash FROM auth_users WHERE username='admin'" > "$work/renamed-hash"
cmp --silent "$work/custom-hash" "$work/renamed-hash"
rm -f "$work/cookies"
refresh_csrf
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 GET "/api/v1/admin/catalogs/$custom_catalog_id" '' bearer
[[ "$(field data.code)" == CITEST ]]

printf 'API journal stage: storage failure preserves business response and reports missed writes\n'
# Chỉ đổi tên bảng trên DB CI sau khởi tạo: giả lập storage log lỗi, không phá DB nghiệp vụ.
renamed_sql 'ALTER TABLE api_request_journal RENAME TO api_request_journal_paused_ci' >/dev/null
call 200 GET /api/v1/catalogs/VEHICLE_TYPE
journal_missed_trace=$(field traceId)
call 503 GET /api/v1/admin/system/api-journal '' cookie
assert_code COM007
renamed_sql 'ALTER TABLE api_request_journal_paused_ci RENAME TO api_request_journal' >/dev/null
call 200 GET /api/v1/admin/system/api-journal '' cookie
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["failedWrites"]>=1 and d["storage"]=="POSTGRESQL"' "$result"
[[ "$(renamed_sql "SELECT count(*) FROM api_request_journal WHERE trace_id='$journal_missed_trace'")" == 0 ]]

printf 'Traffic/monitoring stage: per-node memory quota, protected scrape and database recovery without Redis\n'
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
start_app vietroute-auth-ci 18080 vietroute_ci 3
start_app vietroute-auth-ci2 18081 vietroute_ci 3
for port in 18080 18081; do
  curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors \
    --retry-connrefused --max-time 5 "http://127.0.0.1:$port/health" > /dev/null
done
for port in 18080 18081; do
  for attempt in {1..3}; do
    status=$(curl --silent --show-error --max-time 10 -o "$result" -w '%{http_code}' "http://127.0.0.1:$port/api/v1/auth/csrf")
    [[ "$status" == 200 ]]
  done
done
status=$(curl --silent --show-error --max-time 10 -D "$work/rate-headers" -o "$result" -w '%{http_code}' \
  -H 'X-Forwarded-For: 192.0.2.222' http://127.0.0.1:18081/api/v1/auth/csrf)
[[ "$status" == 429 ]]
assert_code COM012
grep -qi '^Retry-After:' "$work/rate-headers"
scrape() {
  curl --fail --silent --show-error --max-time 5 -H "Authorization: Bearer $metrics_token" "http://127.0.0.1:${1:-18080}/internal/metrics" -o "$work/metrics.txt"
}
[[ "$(curl --silent --show-error --max-time 5 -o /dev/null -w '%{http_code}' "$base/internal/metrics")" == 401 ]]
scrape
grep -q '^vietroute_http_limited_total ' "$work/metrics.txt"
grep -q '^jvm_memory_used_bytes' "$work/metrics.txt"
grep -q '^hikaricp_connections_acquire_seconds_count' "$work/metrics.txt"
grep -q '^vietroute_http_duration_seconds_bucket' "$work/metrics.txt"
if grep -q "$metrics_token" "$work/metrics.txt"; then exit 1; fi
for attempt in {1..20}; do
  scrape
  if grep -q '^vietroute_database_available 1' "$work/metrics.txt"; then break; fi
  sleep 2
done
grep -q '^vietroute_database_available 1' "$work/metrics.txt"
# Dừng DB tạm sau mọi kiểm tra dữ liệu. Scrape vẫn phải hoạt động dù JWT/DB/quota không sẵn sàng.
docker stop vietroute-db-ci >/dev/null
for attempt in {1..25}; do
  scrape
  if grep -q '^vietroute_database_available 0' "$work/metrics.txt"; then break; fi
  sleep 2
done
grep -q '^vietroute_database_available 0' "$work/metrics.txt"
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18080/health > /dev/null
docker start vietroute-db-ci >/dev/null
for attempt in {1..25}; do
  scrape
  if grep -q '^vietroute_database_available 1' "$work/metrics.txt"; then break; fi
  sleep 2
done
grep -q '^vietroute_database_available 1' "$work/metrics.txt"
# Mất một node không làm scrape node còn lại thất bại; không coi đây là benchmark production.
docker stop vietroute-auth-ci2 >/dev/null
scrape
if curl --fail --silent --max-time 3 -H "Authorization: Bearer $metrics_token" http://127.0.0.1:18081/internal/metrics >/dev/null 2>&1; then exit 1; fi
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
source "$(dirname "${BASH_SOURCE[0]}")/verify-admin-recovery.sh"
source "$(dirname "${BASH_SOURCE[0]}")/verify-pooler.sh"
printf 'Function permissions, durable workers, per-node quota, metrics isolation and DB/node recovery checks passed.\n'
