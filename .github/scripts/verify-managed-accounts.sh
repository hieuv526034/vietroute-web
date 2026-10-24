#!/usr/bin/env bash
# Được source sau verify-account.sh, trước kiểm tombstone role DRIVER; chỉ PostgreSQL Docker tạm, không chạy trên Neon hoặc gửi mail.
managed_admin_token="$token"
printf 'Managed accounts stage: create, multi-role, temporary password, status and effective policy\n'
token="$managed_admin_token"
for accounts_locale in vi en; do
  call 200 GET "/operations/accounts?lang=$accounts_locale" '' bearer
  grep -q 'account-edit-dialog' "$result"
  if grep -q '??ui\.' "$result"; then exit 1; fi
done
call 200 GET '/api/v1/admin/accounts?username=&role=&active=' '' bearer
call 200 GET '/api/v1/admin/accounts?username=system' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
call 400 GET '/api/v1/admin/accounts?username=%25' '' bearer
call 400 GET '/api/v1/admin/accounts?size=101' '' bearer
# Tự tạo role fixture riêng, không phụ thuộc quyền hoặc tombstone của bộ kiểm khác.
printf '%s\n' '{"nameVi":"Vai trò tài khoản CI","nameEn":"CI account role","active":true,"version":0}' > "$work/managed-registry.json"
for managed_role in ACCOUNT_ENTRY_CI ACCOUNT_ALT_CI; do
  call 201 POST "/api/v1/admin/access/registry/roles/$managed_role" "$work/managed-registry.json" bearer
done
managed_role_version=$(sql "SELECT permission_version FROM auth_roles WHERE code='ACCOUNT_ENTRY_CI'")
printf '{"permissions":["CATALOG_VIEW","CATALOG_CREATE"],"version":%s}\n' "$managed_role_version" > "$work/managed-grants.json"
call 200 PUT /api/v1/admin/access/roles/ACCOUNT_ENTRY_CI/permissions "$work/managed-grants.json" bearer
CI_ACCOUNT_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"manageddriver","displayName":"Tài xế CI","password":os.environ["CI_ACCOUNT_PASSWORD"],"roles":["DRIVER","ACCOUNT_ENTRY_CI"]},sys.stdout)' > "$work/managed-create.json"
call 201 POST /api/v1/admin/accounts "$work/managed-create.json" bearer
managed_id=$(field data.id)
managed_version=$(field data.version)
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"]; assert d["active"] and d["mustChangePassword"] and set(d["roles"])=={"DRIVER","ACCOUNT_ENTRY_CI"}; assert not any(x in d for x in ("password","passwordHash","tokenVersion","email"))' "$result"
[[ "$(sql "SELECT created_by=1 AND updated_by=1 AND password_hash LIKE '\$2%' FROM auth_users WHERE id=$managed_id")" == t ]]
call 409 POST /api/v1/admin/accounts "$work/managed-create.json" bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));d["username"]="managedinvalid";d["roles"]=["UNKNOWN_CI"];json.dump(d,sys.stdout)' "$work/managed-create.json" > "$work/managed-invalid.json"
call 400 POST /api/v1/admin/accounts "$work/managed-invalid.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='managedinvalid'")" == 0 ]]
call 200 GET '/api/v1/admin/accounts?username=MANAGED&role=DRIVER&active=true&size=1' '' bearer
[[ "$(field data.totalElements)" == 1 ]]
call 200 GET "/api/v1/admin/accounts/$managed_id/access" '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert all(not f["view"] and not f["actions"] for f in d["capabilities"])' "$result"
login_body manageddriver "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 GET /api/v1/auth/me '' bearer
call 403 GET /api/v1/admin/accounts '' bearer
assert_code AUTH011
call 403 POST /api/v1/admin/catalogs "$work/catalog.json" bearer
assert_code AUTH011
CI_OLD="$admin_password" CI_NEW="$next_password" python3 -c 'import json,os,sys;json.dump({"currentPassword":os.environ["CI_OLD"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/managed-password.json"
call 200 POST /api/v1/auth/change-password "$work/managed-password.json" bearer
managed_token=$(field data.accessToken)
token="$managed_token"
call 403 GET /operations/accounts '' bearer
assert_code AUTH003
call 403 GET /api/v1/admin/accounts '' bearer
assert_code AUTH003
# Preview phải áp dụng policy của user đích dù người xem ADMIN được miễn policy bổ sung.
token="$managed_admin_token"
call 200 GET "/api/v1/admin/accounts/$managed_id/access" '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert next(f for f in d["capabilities"] if f["function"]=="CATALOG")["view"]' "$result"
printf '%s\n' '{"kind":"MENU","target":"GET /admin","nameVi":"Chặn menu CI","nameEn":"CI blocked menu","enabled":false,"permissions":[],"roles":[],"version":0}' > "$work/managed-policy.json"
call 201 POST /api/v1/admin/access/policies "$work/managed-policy.json" bearer
# field() không đọc chỉ số mảng; lấy ID từ projection tường minh.
managed_policy=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["data"][0]["id"])' "$result")
call 200 GET "/api/v1/admin/accounts/$managed_id/access" '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert not next(f for f in d["capabilities"] if f["function"]=="CATALOG")["view"]' "$result"
call 200 DELETE "/api/v1/admin/access/policies/$managed_policy?version=0" '' bearer
# Gán role hiện hành rồi kiểm preview bằng cùng guard/capabilities, giữ tokenVersion và version thực tế.
token="$managed_admin_token"
managed_version=$(sql "SELECT version FROM auth_users WHERE id=$managed_id")
printf '{"roles":["DRIVER","ACCOUNT_ALT_CI"],"version":%s}\n' "$managed_version" > "$work/managed-roles.json"
call 200 PUT "/api/v1/admin/access/users/$managed_id/roles" "$work/managed-roles.json" bearer
token="$managed_token"
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
login_body manageddriver "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
managed_token=$(field data.accessToken)
token="$managed_admin_token"
call 200 GET "/api/v1/admin/accounts/$managed_id/access" '' bearer
# ACCOUNT_ALT_CI chưa được cấp quyền: preview không suy quyền nghiệp vụ từ tên role DRIVER.
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert d["user"]["mustChangePassword"] is False;assert {"DRIVER","ACCOUNT_ALT_CI"}==set(d["user"]["roles"])' "$result"
managed_version=$(sql "SELECT version FROM auth_users WHERE id=$managed_id")
printf '{"active":false,"version":%s}\n' "$managed_version" > "$work/managed-state.json"
sql "INSERT INTO auth_account_challenges(user_id,purpose,token_hash,token_version,expires_at) SELECT id,'RESET',repeat('a',64),token_version,CURRENT_TIMESTAMP+INTERVAL '15 minutes' FROM auth_users WHERE id=$managed_id" >/dev/null
call 200 PUT "/api/v1/admin/accounts/$managed_id/state" "$work/managed-state.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_account_challenges WHERE user_id=$managed_id")" == 0 ]]
call 409 PUT "/api/v1/admin/accounts/$managed_id/state" "$work/managed-state.json" bearer
assert_code COM008
token="$managed_token"
call 401 GET /api/v1/auth/me '' bearer
assert_code AUTH004
token="$managed_admin_token"
call 200 GET "/api/v1/admin/accounts/$managed_id/access" '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert d["user"]["active"] is False;assert all(not f["view"] and not f["actions"] for f in d["capabilities"])' "$result"
managed_version=$(sql "SELECT version FROM auth_users WHERE id=$managed_id")
printf '{"active":true,"version":%s}\n' "$managed_version" > "$work/managed-state.json"
call 200 PUT "/api/v1/admin/accounts/$managed_id/state" "$work/managed-state.json" bearer
login_body manageddriver "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token="$managed_admin_token"
printf '%s\n' '{"active":false,"version":0}' > "$work/managed-state.json"
call 403 PUT /api/v1/admin/accounts/0/state "$work/managed-state.json" bearer
call 403 PUT /api/v1/admin/accounts/1/state "$work/managed-state.json" bearer
# Dọn liên kết DRIVER của fixture qua API, giữ tài khoản/lịch sử; bộ kiểm kế tiếp cần xóa role DRIVER chưa có tham chiếu.
managed_version=$(sql "SELECT version FROM auth_users WHERE id=$managed_id")
printf '{"roles":["CUSTOMER","ACCOUNT_ALT_CI"],"version":%s}\n' "$managed_version" > "$work/managed-cleanup.json"
call 200 PUT "/api/v1/admin/access/users/$managed_id/roles" "$work/managed-cleanup.json" bearer
printf 'Managed accounts create, safe response, first password change, assignment and revocation passed.\n'
