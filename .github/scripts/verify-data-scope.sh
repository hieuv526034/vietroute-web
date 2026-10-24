#!/usr/bin/env bash
# Chỉ source trong PostgreSQL Docker CI, sau guard DB và trước bơm registry/cost; không dùng .env/Neon.
printf 'Data scope stage: scoped roles, ID isolation, live revocation, versions and atomic history\n'
token="$tree_admin_token"
for scope_role in SCOPED_EDIT_CI SCOPED_VIEW_CI; do
  printf '%s\n' '{"nameVi":"Role phạm vi CI","nameEn":"Scoped role CI","active":true,"version":0}' > "$work/scope-role.json"
  call 201 POST "/api/v1/admin/access/registry/roles/$scope_role" "$work/scope-role.json" bearer
done
printf '%s\n' '{"permissions":["BOOKING_LIST","BOOKING_DETAIL","ACCESS_READ","ACCESS_ASSIGN"],"version":0}' > "$work/scope-permissions.json"
call 200 PUT '/api/v1/admin/access/roles/SCOPED_EDIT_CI/permissions' "$work/scope-permissions.json" bearer
printf '%s\n' '{"permissions":["BOOKING_LIST"],"version":0}' > "$work/scope-permissions.json"
call 200 PUT '/api/v1/admin/access/roles/SCOPED_VIEW_CI/permissions' "$work/scope-permissions.json" bearer
for scope_code in SCOPE_A_CI SCOPE_B_CI SCOPE_C_CI; do
  printf '{"code":"%s","nameVi":"Phạm vi CI","nameEn":"Scope CI","active":true,"version":0}\n' "$scope_code" > "$work/scope.json"
  call 201 POST '/api/v1/admin/access/scopes' "$work/scope.json" bearer
done
scope_a=$(sql "SELECT id FROM auth_data_scopes WHERE code='SCOPE_A_CI'")
scope_b=$(sql "SELECT id FROM auth_data_scopes WHERE code='SCOPE_B_CI'")
scope_c=$(sql "SELECT id FROM auth_data_scopes WHERE code='SCOPE_C_CI'")
call 200 GET '/api/v1/admin/access/scopes?query=SCOPE_&size=1&page=1' '' bearer
[[ "$(field data.totalElements)" == 3 ]]
call 200 GET '/api/v1/admin/access/scopes?query=SCOPE%25' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
CI_ACCOUNT_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"scopeduserci","displayName":"Scoped account CI","password":os.environ["CI_ACCOUNT_PASSWORD"],"roles":["SCOPED_EDIT_CI","SCOPED_VIEW_CI"]},sys.stdout)' > "$work/scope-user.json"
call 201 POST /api/v1/admin/accounts "$work/scope-user.json" bearer
scope_user=$(field data.id)
printf '%s\n' '{"roles":["SCOPED_EDIT_CI"],"active":true,"version":0}' > "$work/scope-membership.json"
call 200 PUT "/api/v1/admin/access/scopes/$scope_a/members/$scope_user" "$work/scope-membership.json" bearer
printf '%s\n' '{"roles":["SCOPED_VIEW_CI"],"active":true,"version":0}' > "$work/scope-membership.json"
call 200 PUT "/api/v1/admin/access/scopes/$scope_b/members/$scope_user" "$work/scope-membership.json" bearer
call 409 PUT "/api/v1/admin/access/scopes/$scope_b/members/$scope_user" "$work/scope-membership.json" bearer
printf '%s\n' '{"roles":["ADMIN"],"active":true,"version":0}' > "$work/scope-invalid.json"
call 400 PUT "/api/v1/admin/access/scopes/$scope_c/members/$scope_user" "$work/scope-invalid.json" bearer
printf '%s\n' '{"roles":["DRIVER"],"active":true,"version":0}' > "$work/scope-invalid.json"
call 400 PUT "/api/v1/admin/access/scopes/$scope_c/members/$scope_user" "$work/scope-invalid.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_scope_memberships WHERE scope_id=$scope_c AND user_id=$scope_user")" == 0 ]]

# Mật khẩu tạm vẫn chưa được dùng API membership; đổi mật khẩu qua auth chuẩn, không tự phát JWT thủ công.
login_body scopeduserci "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 403 GET /api/v1/auth/scopes '' bearer
assert_code AUTH011
CI_OLD="$admin_password" CI_NEW="$next_password" python3 -c 'import json,os,sys;json.dump({"currentPassword":os.environ["CI_OLD"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/scope-password.json"
call 200 POST /api/v1/auth/change-password "$work/scope-password.json" bearer
scope_user_token=$(field data.accessToken)
token="$scope_user_token"
call 200 GET /api/v1/auth/scopes '' bearer
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert {x["code"] for x in v}=={"SCOPE_A_CI","SCOPE_B_CI"}' "$result"
call 200 GET "/api/v1/auth/scopes/$scope_a/permissions" '' bearer
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert "BOOKING_DETAIL" in v["permissions"] and "BOOKING_LIST" in v["permissions"]' "$result"
call 200 GET "/api/v1/auth/scopes/$scope_b/permissions" '' bearer
python3 -c 'import json,sys;assert json.load(open(sys.argv[1]))["data"]["permissions"]==["BOOKING_LIST"]' "$result"
call 403 GET "/api/v1/auth/scopes/$scope_c/permissions" '' bearer
call 403 GET /api/v1/admin/access/scopes '' bearer
call 403 PUT "/api/v1/admin/access/scopes/$scope_c/members/$scope_user" "$work/scope-invalid.json" bearer
token="$tree_admin_token"
scope_version=$(sql "SELECT token_version FROM auth_users WHERE id=$scope_user")
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/scope-tools" src/test/resources/dev/DataScopeInspector.java
docker cp "$work/scope-tools" vietroute-auth-ci:/tmp/scope-tools >/dev/null
scope_check() {
  docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci -e CI_GUARD_DB_USER=vietroute_ci \
    -e CI_GUARD_DB_PASSWORD="$db_password" -e CI_GUARD_DB_SCHEMA=vietroute vietroute-auth-ci \
    java -cp '/tmp/scope-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' \
    DataScopeInspector "$scope_user" "${scope_version}" "$1" "$2" "$3" "${@:4}"
}
scope_check "$scope_a" DETAIL true "$scope_b"
scope_check "$scope_b" LIST true
scope_check "$scope_b" DETAIL false
scope_check "$scope_c" LIST false
sql "UPDATE auth_scope_memberships SET active=false WHERE scope_id=$scope_b AND user_id=$scope_user" >/dev/null
scope_check "$scope_b" LIST false
sql "UPDATE auth_scope_memberships SET active=true WHERE scope_id=$scope_b AND user_id=$scope_user; UPDATE auth_roles SET active=false WHERE code='SCOPED_EDIT_CI'" >/dev/null
scope_check "$scope_a" DETAIL false
sql "UPDATE auth_roles SET active=true WHERE code='SCOPED_EDIT_CI'; UPDATE auth_permissions SET active=false WHERE code='BOOKING_DETAIL'" >/dev/null
scope_check "$scope_a" DETAIL false
sql "UPDATE auth_permissions SET active=true WHERE code='BOOKING_DETAIL'; UPDATE auth_users SET active=false WHERE id=$scope_user" >/dev/null
scope_check "$scope_a" DETAIL false
sql "UPDATE auth_users SET active=true WHERE id=$scope_user; UPDATE auth_data_scopes SET active=false WHERE id=$scope_a" >/dev/null
scope_check "$scope_a" DETAIL false
sql "UPDATE auth_data_scopes SET active=true WHERE id=$scope_a" >/dev/null
scope_check "$scope_a" DETAIL true

# Audit lỗi phải rollback cả membership version và mapping trước đó.
cat > "$work/scope-audit-fail.sql" <<'SQL'
CREATE FUNCTION ci_scope_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.event_type='SCOPE_MEMBERSHIP' THEN RAISE EXCEPTION 'CI audit refused' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ci_scope_audit_failure BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION ci_scope_audit_failure();
SQL
docker exec -i -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -U vietroute_ci -d vietroute_ci -v ON_ERROR_STOP=1 < "$work/scope-audit-fail.sql" >/dev/null
printf '%s\n' '{"roles":[],"active":false,"version":1}' > "$work/scope-membership.json"
call 503 PUT "/api/v1/admin/access/scopes/$scope_b/members/$scope_user" "$work/scope-membership.json" bearer
assert_code COM007
[[ "$(sql "SELECT version FROM auth_scope_memberships WHERE scope_id=$scope_b AND user_id=$scope_user")" == 1 ]]
scope_check "$scope_b" LIST true
sql 'DROP TRIGGER ci_scope_audit_failure ON auth_access_changes; DROP FUNCTION ci_scope_audit_failure()' >/dev/null
call 200 PUT "/api/v1/admin/access/scopes/$scope_b/members/$scope_user" "$work/scope-membership.json" bearer
scope_check "$scope_b" LIST false
printf '%s\n' '{"roles":["SCOPED_VIEW_CI"],"active":true,"version":2}' > "$work/scope-membership.json"
call 200 PUT "/api/v1/admin/access/scopes/$scope_b/members/$scope_user" "$work/scope-membership.json" bearer

# Giữ global role không đổi không làm mất scoped role/audit cũ; thu hồi role thật dọn FK, tăng version và ghi history.
scope_user_version=$(sql "SELECT version FROM auth_users WHERE id=$scope_user")
printf '{"roles":["SCOPED_EDIT_CI","SCOPED_VIEW_CI"],"version":%s}\n' "$scope_user_version" > "$work/scope-global.json"
call 200 PUT "/api/v1/admin/access/users/$scope_user/roles" "$work/scope-global.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_scope_roles WHERE user_id=$scope_user")" == 2 ]]
scope_user_version=$(field data.version)
scope_version=$(sql "SELECT token_version FROM auth_users WHERE id=$scope_user")
scope_check "$scope_a" DETAIL true
printf '{"roles":["SCOPED_VIEW_CI"],"version":%s}\n' "$scope_user_version" > "$work/scope-global.json"
call 200 PUT "/api/v1/admin/access/users/$scope_user/roles" "$work/scope-global.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_scope_roles WHERE user_id=$scope_user AND scope_id=$scope_a")" == 0 ]]
[[ "$(sql "SELECT version FROM auth_scope_memberships WHERE user_id=$scope_user AND scope_id=$scope_a")" == 2 ]]
[[ "$(sql "SELECT before_state->'roles'='[\"SCOPED_EDIT_CI\"]'::jsonb AND after_state->'roles'='[]'::jsonb FROM auth_access_changes WHERE event_type='SCOPE_MEMBERSHIP' AND target_key='$scope_a:$scope_user' ORDER BY id DESC LIMIT 1")" == t ]]
scope_check "$scope_b" LIST false # tokenVersion cũ cũng bị từ chối dù membership B còn hiệu lực.
scope_version=$(sql "SELECT token_version FROM auth_users WHERE id=$scope_user")
scope_check "$scope_a" DETAIL false
scope_check "$scope_b" LIST true
printf 'Data scope SQL guard, cross-scope roles/rows, revocation, FK/version and atomic history checks passed.\n'
