#!/usr/bin/env bash
# Chỉ PostgreSQL/container CI: phá dữ liệu có kiểm soát để chứng minh đường cứu hộ, không chạy trên Neon.
printf 'Admin recovery stage: bootstrap identity, missing role/mappings, atomic history and preserved password\n'
base=http://127.0.0.1:18080
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
recovery_original_hash=$(sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")
CI_PASSWORD="$next_password" python3 -c 'import json,os,sys;json.dump({"currentPassword":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/recovery.json"

# ADMIN khác không được thấy nút hoặc gọi đường cứu hộ, và không được gỡ ADMIN khỏi bootstrap.
CI_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"recoveryotherci","displayName":"Recovery other CI","password":os.environ["CI_PASSWORD"],"roles":["ADMIN"]},sys.stdout)' > "$work/recovery-other.json"
call 201 POST /api/v1/admin/accounts "$work/recovery-other.json" bearer
login_body recoveryotherci "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 403 POST /api/v1/auth/admin-recovery "$work/recovery.json" bearer
assert_code AUTH011
CI_OLD="$admin_password" CI_NEW="$next_password" python3 -c 'import json,os,sys;json.dump({"currentPassword":os.environ["CI_OLD"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/recovery-password.json"
call 200 POST /api/v1/auth/change-password "$work/recovery-password.json" bearer
token=$(field data.accessToken)
call 403 POST /api/v1/auth/admin-recovery "$work/recovery.json" bearer
assert_code AUTH012
# Ngay cả role ADMIN cũng phải xác minh email trước khi dùng chức năng, khác bootstrap gốc.
CI_PASSWORD="$next_password" python3 -c 'import json,os,sys;json.dump({"email":"recoveryotherci@example.invalid","currentPassword":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/recovery-email.json"
call 202 POST /api/v1/auth/email "$work/recovery-email.json" bearer
recovery_saved_account_id="$account_id"
account_id=$(sql "SELECT id FROM auth_users WHERE username='recoveryotherci'")
account_code
account_id="$recovery_saved_account_id"
python3 -c 'import json,sys;json.dump({"email":"recoveryotherci@example.invalid","token":open(sys.argv[1]).read()},sys.stdout)' "$work/account-code.txt" > "$work/recovery-activate.json"
call 200 POST /api/v1/auth/activate "$work/recovery-activate.json" cookie
call 403 POST /api/v1/auth/admin-recovery "$work/recovery.json" bearer
assert_code AUTH003
call 200 GET /account '' bearer
if grep -q 'id="admin-recovery-open"' "$result"; then exit 1; fi
recovery_version=$(sql 'SELECT version FROM auth_users WHERE id=1')
printf '{"roles":["CUSTOMER"],"version":%s}\n' "$recovery_version" > "$work/recovery-roles.json"
call 403 PUT /api/v1/admin/access/users/1/roles "$work/recovery-roles.json" bearer
assert_code AUTH003

login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
# Ngừng node thứ hai để không có startup sync tự sửa trong lúc kiểm đường thủ công.
docker stop vietroute-auth-ci2 >/dev/null
sql "DELETE FROM auth_user_roles WHERE role_code='ADMIN'; DELETE FROM auth_role_permissions WHERE role_code='ADMIN'; DELETE FROM auth_roles WHERE code='ADMIN'; DELETE FROM auth_role_permissions WHERE permission_code='CATALOG_DELETE'; DELETE FROM auth_permissions WHERE code='CATALOG_DELETE'; UPDATE auth_permissions SET active=false WHERE code='CATALOG_UPDATE'" >/dev/null
recovery_damaged_ordinary=$(sql "SELECT md5(COALESCE(string_agg(role_code||':'||permission_code,',' ORDER BY role_code,permission_code),'')) FROM auth_role_permissions WHERE role_code<>'ADMIN'")
sql "INSERT INTO auth_access_policies(kind,target,name_vi,name_en,enabled,required_permissions,allowed_roles) VALUES ('MENU','GET /account','Chặn account cũ','Legacy account deny',false,'','')" >/dev/null
call 403 GET /api/v1/admin/access/tree '' bearer
call 200 GET /account '' bearer
grep -q 'id="admin-recovery-open"' "$result"

# Nếu lịch sử hỏng, toàn bộ phục hồi (kể cả role/permission/user tokenVersion) phải rollback.
sql 'CREATE FUNCTION reject_admin_recovery_ci() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='"'"'ADMIN_RECOVERED'"'"' THEN RAISE EXCEPTION '"'"'CI recovery audit unavailable'"'"'; END IF; RETURN NEW; END $$; CREATE TRIGGER reject_admin_recovery_ci BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION reject_admin_recovery_ci()' >/dev/null
call 503 POST /api/v1/auth/admin-recovery "$work/recovery.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_roles WHERE code='ADMIN'")" == 0 ]]
[[ "$(sql "SELECT count(*) FROM auth_permissions WHERE code='CATALOG_DELETE'")" == 0 ]]
sql 'DROP TRIGGER reject_admin_recovery_ci ON auth_access_changes; DROP FUNCTION reject_admin_recovery_ci()' >/dev/null
call 200 POST /api/v1/auth/admin-recovery "$work/recovery.json" bearer
recovery_trace=$(field traceId)
[[ "$(field data.loginRequired)" == True ]]
call 401 GET /api/v1/auth/me '' bearer
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 GET /api/v1/admin/access/tree '' bearer
[[ "$(sql "SELECT count(*) FROM auth_permissions p WHERE p.active AND NOT EXISTS(SELECT 1 FROM auth_role_permissions rp WHERE rp.role_code='ADMIN' AND rp.permission_code=p.code)")" == 0 ]]
[[ "$(sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")" == "$recovery_original_hash" ]]
[[ "$(sql "SELECT md5(COALESCE(string_agg(role_code||':'||permission_code,',' ORDER BY role_code,permission_code),'')) FROM auth_role_permissions WHERE role_code<>'ADMIN'")" == "$recovery_damaged_ordinary" ]]
[[ "$(sql "SELECT count(*) FROM auth_access_changes WHERE event_type='ADMIN_RECOVERED' AND actor_id=1 AND trace_id='$recovery_trace' AND after_state->'roles' ? 'ADMIN'")" == 1 ]]

# Khởi động lại tự sửa mapping bị mất, giữ mật khẩu và không cấp lại ADMIN cho user khác.
sql "DELETE FROM auth_user_roles WHERE user_id=1 AND role_code='ADMIN'; DELETE FROM auth_role_permissions WHERE role_code='ADMIN' AND permission_code='ACCESS_READ'" >/dev/null
docker restart vietroute-auth-ci >/dev/null
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 "$base/health" >/dev/null
login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 200 GET /api/v1/admin/access/tree '' bearer
[[ "$(sql "SELECT md5(password_hash) FROM auth_users WHERE id=1")" == "$recovery_original_hash" ]]
[[ "$(sql "SELECT count(*) FROM auth_user_roles WHERE role_code='ADMIN'")" == 1 ]]

# Lifecycle cùng DAO, username tùy cấu hình; transaction rollback không làm đổi dataset của các kiểm trước.
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/recovery-tools" src/test/resources/dev/AdminRecoveryInspector.java
docker cp "$work/recovery-tools" vietroute-auth-ci:/tmp/recovery-tools >/dev/null
docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" vietroute-auth-ci java -Xmx96m -cp '/tmp/recovery-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' AdminRecoveryInspector
printf 'Admin bootstrap/recovery, immutable identity, password preservation and rollback checks passed.\n'
