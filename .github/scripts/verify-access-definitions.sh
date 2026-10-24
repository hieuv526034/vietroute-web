#!/usr/bin/env bash
# Chỉ DB/container Docker CI; kiểm DAO production, không đọc .env hoặc ghi Neon.
printf 'Access definitions stage: source registry and effective permission expansion\n'
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/definition-tools" src/test/resources/dev/AccessDefinitionInspector.java
docker cp "$work/definition-tools" vietroute-auth-ci:/tmp/definition-tools >/dev/null
docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci \
  -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" -e CI_GUARD_DB_SCHEMA=vietroute \
  vietroute-auth-ci java -Xms16m -Xmx96m -XX:+UseSerialGC -cp '/tmp/definition-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' \
  AccessDefinitionInspector
