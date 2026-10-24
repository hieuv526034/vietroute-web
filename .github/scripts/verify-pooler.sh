#!/usr/bin/env bash
# Chỉ DB/container CI: pooler nằm trong container PostgreSQL tạm, không thêm dịch vụ vào app/Render.
printf 'Pooler stage: schema LOCAL, commit/rollback and application auth through transaction pooling\n'
docker exec --user 0 vietroute-db-ci apk add --no-cache pgbouncer > "$work/pgbouncer-install.log"
cat > "$work/pgbouncer.ini" <<'CONFIG'
[databases]
vietroute_ci = host=127.0.0.1 port=5432 dbname=vietroute_ci
[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
unix_socket_dir = /tmp
auth_type = plain
auth_file = /tmp/pooler-ci/users.txt
ignore_startup_parameters = extra_float_digits
pool_mode = transaction
default_pool_size = 2
max_client_conn = 64
max_prepared_statements = 200
server_reset_query = DISCARD ALL
server_reset_query_always = 1
log_connections = 0
log_disconnections = 0
CONFIG
# Mật khẩu ngẫu nhiên CI đã mask; chỉ dùng trong network Docker tạm, không ghi Git/image hoặc in ra log.
printf '"vietroute_ci" "%s"\n' "$db_password" > "$work/pooler-users.txt"
docker exec --user 0 vietroute-db-ci mkdir -p /tmp/pooler-ci
docker cp "$work/pgbouncer.ini" vietroute-db-ci:/tmp/pooler-ci/pgbouncer.ini >/dev/null
docker cp "$work/pooler-users.txt" vietroute-db-ci:/tmp/pooler-ci/users.txt >/dev/null
docker exec --user 0 vietroute-db-ci chown -R postgres:postgres /tmp/pooler-ci
docker exec --detach --user postgres vietroute-db-ci pgbouncer /tmp/pooler-ci/pgbouncer.ini
for attempt in {1..30}; do
  if docker exec vietroute-db-ci pg_isready -h 127.0.0.1 -p 6432 -U vietroute_ci -d vietroute_ci >/dev/null 2>&1; then break; fi
  sleep 1
done
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/pooler-tools" src/test/resources/dev/TransactionPoolerInspector.java
docker cp "$work/pooler-tools" vietroute-auth-ci:/tmp/pooler-tools >/dev/null
docker exec -e CI_POOLER_DB_URL=jdbc:postgresql://vietroute-db-ci:6432/vietroute_ci \
  -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" \
  vietroute-auth-ci java -Xmx96m -cp '/tmp/pooler-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' TransactionPoolerInspector
# Node thật khởi tạo lại registry dưới cùng pooler: health không đủ, kiểm auth/guard/DAO và journal commit.
start_app vietroute-pooler-ci 18082 vietroute_ci 1000 '' vietroute false 6432 ''
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 http://127.0.0.1:18082/health >/dev/null
pooler_saved_base="$base"; base=http://127.0.0.1:18082
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
for attempt in {1..4}; do
  call 200 GET /api/v1/auth/me '' bearer
  call 200 GET /api/v1/admin/access/tree '' bearer
done
pooler_trace=$(field traceId)
call 200 GET "/api/v1/admin/system/api-journal?traceId=$pooler_trace" '' bearer
[[ "$(field data.records.totalElements)" == 1 ]]
base="$pooler_saved_base"
printf 'Transaction pooler schema isolation, committed writes, rollback and real auth/API journal passed.\n'
