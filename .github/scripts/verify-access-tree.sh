#!/usr/bin/env bash
# Chỉ source trong verify-auth.sh, PostgreSQL Docker tạm; không đọc .env/Neon hoặc gọi nhà cung cấp.
printf 'Access tree stage: bootstrap, actions, multiple roles, delegated assignment and two nodes\n'
tree_admin_token="$foundation_admin_token"
token="$tree_admin_token"
[[ "$(sql 'SELECT count(*) FROM auth_access_functions')" == 11 ]]
[[ "$(sql "SELECT count(*) FROM auth_access_resources WHERE target='POST /api/v1/admin/catalogs' AND function_code='CATALOG' AND permission_match='ANY' AND declared_permissions LIKE '%CATALOG_CREATE%'")" == 1 ]]
call 200 GET /api/v1/admin/access/tree '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; f=next(x for x in d if x["code"]=="CATALOG"); assert f["parent"]=="SYSTEM" and f["viewPermission"]=="CATALOG_VIEW"; a=next(x for x in f["actions"] if x["permission"]=="CATALOG_CREATE"); assert any(x["target"]=="POST /api/v1/admin/catalogs" and x["match"]=="ANY" for x in a["endpoints"])' "$result"
# Lấy bộ quyền từ metadata API thật giống thao tác chọn mẫu; lưu mapping, không có authority bypass toàn cục.
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; codes={a["permission"] for f in d if f["systemBundle"] for a in f["actions"] if not a["permission"].endswith("_WRITE") and any(not e["adminOnly"] for e in a["endpoints"])}; assert "CONFIGURATION_READ" in codes and "ACCESS_ASSIGN" in codes and "CATALOG_CREATE" not in codes and "JOURNAL_VIEW" not in codes; json.dump({"permissions":sorted(codes),"version":0},open(sys.argv[2],"w"))' "$result" "$work/tree-system-permissions.json"
# Bộ lọc dùng chung predicate count/data trên DB, từ khóa nguyên văn không diễn giải wildcard hoặc SQL.
call 200 GET '/api/v1/admin/access/resources?kind=ENDPOINT&query=CATALOG&page=0&size=2' '' bearer
tree_resource_total=$(sql "SELECT count(*) FROM auth_access_resources WHERE kind='ENDPOINT' AND strpos(lower(concat_ws(' ',target,handler,description,resource_group,function_code,declared_permissions)),'catalog')>0")
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["totalElements"]==int(sys.argv[2]) and d["totalElements"]>2 and len(d["content"])==2; assert all(x["kind"]=="ENDPOINT" for x in d["content"])' "$result" "$tree_resource_total"
call 200 GET '/api/v1/admin/access/resources?kind=MENU&query=MediaPageController' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["totalElements"]==1 and d["content"][0]["kind"]=="MENU"' "$result"
call 200 GET '/api/v1/admin/access/resources?query=%25_%27%20OR%20true%20--' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
call 400 GET '/api/v1/admin/access/resources?kind=INVALID' '' bearer
assert_code COM001
tree_long_query=$(printf 'a%.0s' {1..161})
call 400 GET "/api/v1/admin/access/resources?query=$tree_long_query" '' bearer
assert_code COM001
printf '%s\n' '{"nameVi":"Nhập liệu CI","nameEn":"CI entry","active":true,"version":0}' > "$work/tree-registry.json"
for tree_role in ENTRY_CI ALT_CI MANAGER_CI TARGET_CI POWER_CI SYSTEMSET_CI; do
  call 201 POST "/api/v1/admin/access/registry/roles/$tree_role" "$work/tree-registry.json" bearer
done
# Mã quyền ngoài bootstrap được registry DB chấp nhận; LIST và DETAIL là hai hành động độc lập.
for tree_action in LIST DETAIL; do
  call 201 POST "/api/v1/admin/access/registry/permissions/BOOKING_$tree_action" "$work/tree-registry.json" bearer
done
call 200 GET /api/v1/admin/access/permissions '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert next(x for x in d if x["code"]=="BOOKING_LIST")["actions"]==["LIST"]; assert next(x for x in d if x["code"]=="BOOKING_DETAIL")["actions"]==["DETAIL"]' "$result"
call 200 PUT /api/v1/admin/access/roles/SYSTEMSET_CI/permissions "$work/tree-system-permissions.json" bearer
tree_system_user=$(sql "INSERT INTO auth_users(username,display_name,password_hash,active,email_verified_at,must_change_password,created_by,updated_by) SELECT 'treesystem','System set CI',password_hash,true,CURRENT_TIMESTAMP,false,1,1 FROM auth_users WHERE id=1 RETURNING id" | head -n1)
printf '%s\n' '{"roles":["SYSTEMSET_CI"],"version":0}' > "$work/tree-system-roles.json"
call 200 PUT "/api/v1/admin/access/users/$tree_system_user/roles" "$work/tree-system-roles.json" bearer
login_body treesystem "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
for tree_system_path in /operations/access /operations/jobs /operations/configuration /monitoring /api/v1/admin/access/tree /api/v1/admin/access/roles /api/v1/admin/access/resources /api/v1/admin/jobs /api/v1/admin/system/configuration /api/v1/admin/system/runtime; do
  call 200 GET "$tree_system_path" '' bearer
done
for tree_admin_path in /operations/api-journal /api/v1/admin/system/api-journal /api/v1/admin/system/activity/online /api/v1/admin/catalogs/1; do
  call 403 GET "$tree_admin_path" '' bearer
done
call 403 GET /admin '' bearer
call 403 GET /media '' bearer
token="$tree_admin_token"
printf '%s\n' '{"permissions":["CATALOG_VIEW","CATALOG_CREATE"],"version":0}' > "$work/tree-permissions.json"
call 200 PUT /api/v1/admin/access/roles/ENTRY_CI/permissions "$work/tree-permissions.json" bearer
call 200 PUT /api/v1/admin/access/roles/ALT_CI/permissions "$work/tree-permissions.json" bearer
tree_user=$(sql "INSERT INTO auth_users(username,display_name,password_hash,active,email_verified_at,must_change_password,created_by,updated_by) SELECT 'treeentry','Tree entry',password_hash,true,CURRENT_TIMESTAMP,false,1,1 FROM auth_users WHERE id=1 RETURNING id" | head -n1)
printf '%s\n' '{"roles":["ENTRY_CI"],"version":0}' > "$work/tree-roles.json"
call 200 PUT "/api/v1/admin/access/users/$tree_user/roles" "$work/tree-roles.json" bearer
login_body treeentry "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
tree_user_token=$(field data.accessToken)
token="$tree_user_token"
call 200 GET /admin '' bearer
call 403 GET /operations/access '' bearer
call 200 GET /api/v1/auth/capabilities '' bearer
python3 -c 'import json,sys; d=next(x for x in json.load(open(sys.argv[1]))["data"] if x["function"]=="CATALOG"); assert d["view"] and set(d["actions"])=={"VIEW","CREATE"}' "$result"
printf '%s\n' '{"type":"VEHICLE_TYPE","code":"TREECI","nameVi":"Cây quyền","nameEn":"Access tree","sortOrder":1}' > "$work/tree-catalog.json"
call 201 POST /api/v1/admin/catalogs "$work/tree-catalog.json" bearer
tree_catalog=$(field data.id)
[[ "$(field data.createdBy)" == "$tree_user" ]]
call 403 GET "/api/v1/admin/catalogs/$tree_catalog" '' bearer
call 403 PUT "/api/v1/admin/catalogs/$tree_catalog" "$work/tree-catalog.json" bearer
call 403 DELETE "/api/v1/admin/catalogs/$tree_catalog" "$work/tree-catalog.json" bearer
assert_code AUTH003
token="$tree_admin_token"
# Đăng nhập cập nhật audit/bộ đếm và version user; đọc version hiện hành thay vì suy từ số lần gán role.
tree_user_version=$(sql "SELECT version FROM auth_users WHERE id=$tree_user")
printf '{"roles":["ENTRY_CI","ALT_CI"],"version":%s}\n' "$tree_user_version" > "$work/tree-roles.json"
call 200 PUT "/api/v1/admin/access/users/$tree_user/roles" "$work/tree-roles.json" bearer
printf '%s\n' '{"permissions":[],"version":1}' > "$work/tree-revoke.json"
call 200 PUT /api/v1/admin/access/roles/ENTRY_CI/permissions "$work/tree-revoke.json" bearer
call 200 GET "/api/v1/admin/access/users/$tree_user/effective" '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert d["sources"]["CATALOG_CREATE"]==["ALT_CI"] and d["active"]' "$result"
login_body treeentry "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
tree_user_token=$(field data.accessToken)
token="$tree_user_token"
call 200 GET /admin '' bearer
# Bộ kiểm account trước đó đã xóa node thứ hai; tạo lại node cùng bản trên DB CI để kiểm thu hồi quyền chéo node.
start_app vietroute-auth-ci2 18081
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 http://127.0.0.1:18081/health >/dev/null
tree_base="$base"; base=http://127.0.0.1:18081
call 200 GET /api/v1/auth/capabilities '' bearer
python3 -c 'import json,sys; assert "CREATE" in next(x for x in json.load(open(sys.argv[1]))["data"] if x["function"]=="CATALOG")["actions"]' "$result"
base="$tree_base"; token="$tree_admin_token"
call 200 PUT /api/v1/admin/access/roles/ALT_CI/permissions "$work/tree-revoke.json" bearer
token="$tree_user_token"; base=http://127.0.0.1:18081
call 403 POST /api/v1/admin/catalogs "$work/tree-catalog.json" bearer
call 403 GET /admin '' bearer
call 200 GET /api/v1/auth/capabilities '' bearer
python3 -c 'import json,sys; assert not next(x for x in json.load(open(sys.argv[1]))["data"] if x["function"]=="CATALOG")["actions"]' "$result"
base="$tree_base"; token="$tree_admin_token"
docker stop vietroute-auth-ci2 >/dev/null

# Quyền giám sát của role mới không mở thêm API cấu hình; quyền hiệu lực thay ngay khi JWT còn hạn.
printf '%s\n' '{"permissions":["SYSTEM_VIEW","SYSTEM_READ"],"version":2}' > "$work/tree-monitor.json"
call 200 PUT /api/v1/admin/access/roles/ALT_CI/permissions "$work/tree-monitor.json" bearer
token="$tree_user_token"
call 200 GET /api/v1/admin/system/runtime '' bearer
call 403 GET /api/v1/admin/system/configuration '' bearer
call 200 GET /api/v1/auth/capabilities '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert next(x for x in d if x["function"]=="MONITORING")["view"]; assert not next(x for x in d if x["function"]=="CONFIGURATION")["actions"]' "$result"
token="$tree_admin_token"
printf '%s\n' '{"permissions":[],"version":3}' > "$work/tree-monitor-revoke.json"
call 200 PUT /api/v1/admin/access/roles/ALT_CI/permissions "$work/tree-monitor-revoke.json" bearer

# Quyền/role sau này bị tắt không được dùng làm đường tự kích hoạt nâng quyền.
call 201 POST /api/v1/admin/access/registry/permissions/EXTRA_READ "$work/tree-registry.json" bearer
printf '%s\n' '{"permissions":["SYSTEM_READ"],"version":0}' > "$work/tree-power.json"
call 200 PUT /api/v1/admin/access/roles/POWER_CI/permissions "$work/tree-power.json" bearer
# Role được ủy quyền quản trị: không cần ADMIN nhưng không cấp vượt quyền, tự sửa hoặc đụng ADMIN/system.
printf '%s\n' '{"permissions":["ACCESS_VIEW","ACCESS_READ","ACCESS_ASSIGN","ACCESS_CREATE","ACCESS_UPDATE","ACCESS_DELETE","CATALOG_VIEW","CATALOG_CREATE","EXTRA_READ"],"version":0}' > "$work/tree-manager.json"
call 200 PUT /api/v1/admin/access/roles/MANAGER_CI/permissions "$work/tree-manager.json" bearer
tree_manager=$(sql "INSERT INTO auth_users(username,display_name,password_hash,active,email_verified_at,must_change_password,created_by,updated_by) SELECT 'treemanager','Tree manager',password_hash,true,CURRENT_TIMESTAMP,false,1,1 FROM auth_users WHERE id=1 RETURNING id" | head -n1)
printf '%s\n' '{"roles":["MANAGER_CI","POWER_CI"],"version":0}' > "$work/tree-roles.json"
call 200 PUT "/api/v1/admin/access/users/$tree_manager/roles" "$work/tree-roles.json" bearer
printf '%s\n' '{"nameVi":"Tạm tắt","nameEn":"Disabled","active":false,"version":0}' > "$work/tree-disable.json"
call 200 PUT /api/v1/admin/access/registry/roles/POWER_CI "$work/tree-disable.json" bearer
call 200 PUT /api/v1/admin/access/registry/permissions/EXTRA_READ "$work/tree-disable.json" bearer
# Chuẩn bị mapping khi role tắt, không cần bật quyền cũ trong một cửa sổ trước khi sửa.
printf '%s\n' '{"permissions":["SYSTEM_READ"],"version":1}' > "$work/tree-power-edit.json"
call 200 PUT /api/v1/admin/access/roles/POWER_CI/permissions "$work/tree-power-edit.json" bearer
[[ "$(field data.active)" == False ]]
[[ "$(field data.version)" == 2 ]]
call 200 GET /api/v1/admin/access/roles '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; p=next(x for x in d if x["role"]=="POWER_CI"); assert not p["active"] and p["assignedUsers"]==1 and p["version"]==2; a=next(x for x in d if x["role"]=="ADMIN"); assert "CONFIGURATION_READ" in a["permissions"]' "$result"
login_body treemanager "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
printf '%s\n' '{"nameVi":"Tự bật","nameEn":"Self enable","active":true,"version":1}' > "$work/tree-enable.json"
call 403 PUT /api/v1/admin/access/registry/roles/POWER_CI "$work/tree-enable.json" bearer
call 403 PUT /api/v1/admin/access/registry/permissions/EXTRA_READ "$work/tree-enable.json" bearer
[[ "$(sql "SELECT NOT active FROM auth_roles WHERE code='POWER_CI'")" == t ]]
[[ "$(sql "SELECT NOT active FROM auth_permissions WHERE code='EXTRA_READ'")" == t ]]
call 200 GET /operations/access '' bearer
call 200 GET /api/v1/admin/access/tree '' bearer
call 200 GET /api/v1/admin/access/users '' bearer
printf '%s\n' '{"permissions":["CATALOG_VIEW","CATALOG_CREATE"],"version":0}' > "$work/tree-target.json"
call 200 PUT /api/v1/admin/access/roles/TARGET_CI/permissions "$work/tree-target.json" bearer
call 409 PUT /api/v1/admin/access/roles/TARGET_CI/permissions "$work/tree-target.json" bearer
printf '%s\n' '{"permissions":["SYSTEM_READ"],"version":1}' > "$work/tree-overgrant.json"
call 403 PUT /api/v1/admin/access/roles/TARGET_CI/permissions "$work/tree-overgrant.json" bearer
call 403 PUT /api/v1/admin/access/roles/ADMIN/permissions "$work/tree-target.json" bearer
call 403 PUT /api/v1/admin/access/registry/roles/ADMIN "$work/tree-registry.json" bearer
printf '%s\n' '{"roles":["ADMIN"],"version":2}' > "$work/tree-roles.json"
call 403 PUT "/api/v1/admin/access/users/$tree_user/roles" "$work/tree-roles.json" bearer
call 403 PUT "/api/v1/admin/access/users/$tree_manager/roles" "$work/tree-roles.json" bearer
call 403 PUT /api/v1/admin/access/users/1/roles "$work/tree-roles.json" bearer
call 403 PUT /api/v1/admin/access/users/0/roles "$work/tree-roles.json" bearer
tree_user_version=$(sql "SELECT version FROM auth_users WHERE id=$tree_user")
printf '{"roles":["TARGET_CI"],"version":%s}\n' "$tree_user_version" > "$work/tree-roles.json"
call 200 PUT "/api/v1/admin/access/users/$tree_user/roles" "$work/tree-roles.json" bearer
[[ "$(field data.updatedBy)" == "$tree_manager" ]]
[[ "$(sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='TARGET_CI' AND permission_code IN ('CATALOG_VIEW','CATALOG_CREATE')")" == 2 ]]
token="$tree_admin_token"
printf 'Upgrade stage: V012 snapshot keeps grants/password; VIEW backfill runs exactly once\n'
sql 'CREATE DATABASE view_upgrade_ci' >/dev/null
docker exec vietroute-db-ci pg_dump -U vietroute_ci -d vietroute_ci -Fc |
  docker exec -i vietroute-db-ci pg_restore -U vietroute_ci -d view_upgrade_ci --no-owner
upgrade_sql() { docker exec -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -U vietroute_ci -d view_upgrade_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
# Khôi phục hình dạng V012 trong DB clone cô lập; checksum V001–V012 và toàn bộ dữ liệu gốc giữ nguyên.
upgrade_sql "ALTER TABLE auth_access_resources DROP COLUMN function_code; ALTER TABLE auth_access_resources DROP COLUMN permission_match; ALTER TABLE auth_access_resources DROP COLUMN admin_only; DROP TABLE auth_access_functions; DROP TABLE auth_scope_roles; DROP TABLE auth_scope_memberships; DROP TABLE auth_data_scopes; DELETE FROM vietroute_schema_history WHERE version='V015__data_scope_memberships.sql'; DROP TABLE auth_access_changes; DELETE FROM vietroute_schema_history WHERE version='V014__access_change_history.sql'; DELETE FROM auth_role_permissions WHERE permission_code LIKE '%_VIEW' OR permission_code='CONFIGURATION_READ'; DELETE FROM auth_permissions WHERE code LIKE '%_VIEW' OR code='CONFIGURATION_READ'; DELETE FROM vietroute_schema_history WHERE version='V013__function_tree_and_view.sql'; INSERT INTO auth_roles(code,name_vi,name_en,permissions_initialized,permission_version) VALUES ('UPGRADE_CI','Nâng cấp','Upgrade',true,4),('LOOKALIKE_CI','Tên gần giống','Similar prefix',true,4); INSERT INTO auth_permissions(code,name_vi,name_en) VALUES ('CATALOGX_READ','Khác danh mục','Different resource'); INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('UPGRADE_CI','CATALOG_READ'),('LOOKALIKE_CI','CATALOGX_READ');" >/dev/null
upgrade_hash=$(upgrade_sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")
docker rm --force vietroute-auth-ci2 >/dev/null
start_app vietroute-auth-ci2 18081 view_upgrade_ci
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 http://127.0.0.1:18081/health >/dev/null
[[ "$(upgrade_sql 'SELECT count(*) FROM vietroute_schema_history')" == 15 ]]
[[ "$(upgrade_sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='UPGRADE_CI' AND permission_code='CATALOG_VIEW'")" == 1 ]]
[[ "$(upgrade_sql "SELECT permission_version=5 AND updated_by=0 FROM auth_roles WHERE code='UPGRADE_CI'")" == t ]]
[[ "$(upgrade_sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='LOOKALIKE_CI' AND permission_code='CATALOG_VIEW'")" == 0 ]]
[[ "$(upgrade_sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")" == "$upgrade_hash" ]]
base=http://127.0.0.1:18081
call 200 GET /api/v1/admin/access/tree '' bearer
printf '%s\n' '{"permissions":["CATALOG_READ"],"version":5}' > "$work/tree-upgrade-revoke.json"
call 200 PUT /api/v1/admin/access/roles/UPGRADE_CI/permissions "$work/tree-upgrade-revoke.json" bearer
docker restart vietroute-auth-ci2 >/dev/null
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 "$base/health" >/dev/null
[[ "$(upgrade_sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='UPGRADE_CI' AND permission_code='CATALOG_VIEW'")" == 0 ]]
[[ "$(upgrade_sql "SELECT permission_version FROM auth_roles WHERE code='UPGRADE_CI'")" == 6 ]]
[[ "$(upgrade_sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")" == "$upgrade_hash" ]]
base="$tree_base"
docker stop vietroute-auth-ci2 >/dev/null
printf 'Function tree, VIEW/action guards, delegation, effective sources and cross-node revocation passed.\n'
