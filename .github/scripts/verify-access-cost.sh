#!/usr/bin/env bash
# Chỉ DB/container CI; đo tải nhỏ có giới hạn, không dùng Neon hoặc coi đây là capacity production.
printf 'Access cost stage: query counts, bounded JDBC concurrency and two-node HTTP workload\n'
sql "UPDATE auth_users SET active=true,must_change_password=false WHERE id=$guard_user" >/dev/null
cost_version=$(sql "SELECT token_version FROM auth_users WHERE id=$guard_user")
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/cost-tools" src/test/resources/dev/AccessCostInspector.java src/test/resources/dev/LoginCostInspector.java
docker cp "$work/cost-tools" vietroute-auth-ci:/tmp/cost-tools >/dev/null
docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci \
  -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" -e CI_GUARD_DB_SCHEMA=vietroute \
  vietroute-auth-ci java -Xms16m -Xmx96m -XX:+UseSerialGC -cp '/tmp/cost-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' \
  AccessCostInspector "$guard_user" "$cost_version" | tee "$work/access-jdbc-cost.json"
docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci \
  -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" -e CI_GUARD_DB_SCHEMA=vietroute \
  -e CI_LOGIN_PASSWORD="$next_password" \
  vietroute-auth-ci java -Xms16m -Xmx96m -XX:+UseSerialGC -cp '/tmp/cost-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' \
  LoginCostInspector | tee "$work/login-jdbc-cost.json"

# Node thứ hai trước đó chạy DB clone phục vụ kiểm nâng schema; dựng lại trên cùng DB cho workload này.
docker rm --force vietroute-auth-ci2 >/dev/null
start_app vietroute-auth-ci2 18081
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 http://127.0.0.1:18081/health >/dev/null
CI_ACCESS_LOAD_TOKEN="$tree_admin_token" python3 src/test/resources/dev/access-load.py | tee "$work/access-http-cost.json"
for cost_port in 18080 18081; do
  curl --fail --silent --show-error --max-time 5 -H "Authorization: Bearer $metrics_token" "http://127.0.0.1:$cost_port/internal/metrics" > "$work/cost-metrics-$cost_port.txt"
  printf 'Pool metrics after CI workload, node port=%s\n' "$cost_port"
  grep -E '^(vietroute_pool_waiting |hikaricp_connections_acquire_seconds_(count|sum|max))' "$work/cost-metrics-$cost_port.txt"
done
printf 'Access cost bounded CI workload passed; values are not a Neon/Render production capacity claim.\n'
