#!/usr/bin/env bash
# Chỉ source cuối verify-auth.sh: toàn bộ sửa dữ liệu dưới đây thuộc PostgreSQL Docker CI, không dùng .env/Neon.
printf 'Database guard stage: direct bean checks against live PostgreSQL without JWT/filter authorities\n'
token="$tree_admin_token"
printf '%s\n' '{"nameVi":"Kiểm DB guard","nameEn":"DB guard CI","active":true,"version":0}' > "$work/db-guard-role.json"
call 201 POST /api/v1/admin/access/registry/roles/DB_GUARD_CI "$work/db-guard-role.json" bearer
printf '%s\n' '{"permissions":["BOOKING_LIST"],"version":0}' > "$work/db-guard-permission.json"
call 200 PUT /api/v1/admin/access/roles/DB_GUARD_CI/permissions "$work/db-guard-permission.json" bearer
guard_user=$(sql "INSERT INTO auth_users(username,display_name,password_hash,active,email_verified_at,must_change_password) SELECT 'dbguardci','DB guard CI',password_hash,true,CURRENT_TIMESTAMP,false FROM auth_users WHERE id=1 RETURNING id" | sed -n '1p')
sql "INSERT INTO auth_user_roles(user_id,role_code) VALUES ($guard_user,'DB_GUARD_CI')" >/dev/null
# Compile helper test ngoài image/WAR; chỉ copy vào container tạm đã chạy để gọi class production.
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/guard-tools" src/test/resources/dev/DatabaseGuardInspector.java
docker cp "$work/guard-tools" vietroute-auth-ci:/tmp/guard-tools >/dev/null
guard_check() {
  docker exec -e CI_GUARD_DB_URL=jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci \
    -e CI_GUARD_DB_USER=vietroute_ci -e CI_GUARD_DB_PASSWORD="$db_password" -e CI_GUARD_DB_SCHEMA=vietroute \
    vietroute-auth-ci java -cp '/tmp/guard-tools:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' DatabaseGuardInspector "$guard_user" "${2:-0}" "$1"
}
guard_check true
# Bỏ email đã xác minh phải mất quyền ngay dù role/token không thay đổi.
sql "UPDATE auth_users SET email_verified_at=NULL WHERE id=$guard_user" >/dev/null
guard_check false
sql "UPDATE auth_users SET email_verified_at=CURRENT_TIMESTAMP WHERE id=$guard_user" >/dev/null
guard_check true
# Cùng principal/tokenVersion và chuỗi authority giả: thu hồi mapping có hiệu lực ngay lần gọi hàm tiếp theo.
sql "DELETE FROM auth_role_permissions WHERE role_code='DB_GUARD_CI'" >/dev/null
guard_check false
sql "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('DB_GUARD_CI','BOOKING_LIST')" >/dev/null
sql "UPDATE auth_roles SET active=false WHERE code='DB_GUARD_CI'" >/dev/null
guard_check false
sql "UPDATE auth_roles SET active=true WHERE code='DB_GUARD_CI'; UPDATE auth_permissions SET active=false WHERE code='BOOKING_LIST'" >/dev/null
guard_check false
sql "UPDATE auth_permissions SET active=true WHERE code='BOOKING_LIST'; UPDATE auth_users SET must_change_password=true WHERE id=$guard_user" >/dev/null
guard_check false
sql "UPDATE auth_users SET must_change_password=false,token_version=token_version+1 WHERE id=$guard_user" >/dev/null
guard_check false
guard_check true 1
sql "UPDATE auth_users SET active=false WHERE id=$guard_user" >/dev/null
guard_check false 1
printf 'Database guard direct SQL mapping, inactive role/permission/account, temporary password and revoked token checks passed.\n'
