#!/usr/bin/env bash
# Chỉ source sau verify-account.sh trong DB Docker tạm; không đọc .env hoặc gọi provider thật.
printf 'Foundation stage: immutable ADMIN, mutable roles, annotations and own profile/avatar\n'
foundation_admin_token="$token"
# Giữ khóa quản trị từ session DB CI rồi đăng ký song song: request phải chờ cùng khóa trước khi tạo user.
docker exec -e PGAPPNAME=foundation-role-lock vietroute-db-ci psql -U vietroute_ci -d vietroute_ci -v ON_ERROR_STOP=1 \
  -c "BEGIN; SELECT pg_advisory_xact_lock(781205611); SELECT pg_sleep(60); COMMIT" > "$work/role-lock.log" 2>&1 &
foundation_lock_pid=$!
for attempt in {1..40}; do
  if [[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE application_name='foundation-role-lock' AND wait_event='PgSleep'")" -gt 0 ]]; then break; fi
  sleep 0.1
done
[[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE application_name='foundation-role-lock' AND wait_event='PgSleep'")" -gt 0 ]]
CI_PASSWORD="$admin_password" python3 -c 'import json,os,sys; json.dump({"username":"rolelockci","displayName":"Role lock CI","email":"rolelockci@example.invalid","password":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/role-lock-register.json"
(call 202 POST /api/v1/auth/register "$work/role-lock-register.json" cookie) > "$work/role-lock-request.log" 2>&1 &
foundation_register_pid=$!
for attempt in {1..40}; do
  if [[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE application_name<>'foundation-role-lock' AND wait_event='advisory' AND query LIKE '%pg_advisory_xact_lock(781205611)%'")" -gt 0 ]]; then break; fi
  sleep 0.1
done
[[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE application_name<>'foundation-role-lock' AND wait_event='advisory' AND query LIKE '%pg_advisory_xact_lock(781205611)%'")" -gt 0 ]]
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='rolelockci'")" == 0 ]]
sql "SELECT pg_cancel_backend(pid) FROM pg_stat_activity WHERE application_name='foundation-role-lock'" >/dev/null
wait "$foundation_lock_pid" || true # Session CI được chủ động hủy để giải phóng transaction lock.
wait "$foundation_register_pid"
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='rolelockci'")" == 1 ]]
printf 'Registration waits for the shared role-assignment lock before creating CUSTOMER.\n'
printf '%s\n' '{"nameVi":"Không được đổi","nameEn":"Immutable","active":true,"version":0}' > "$work/foundation-registry.json"
call 403 PUT /api/v1/admin/access/registry/roles/ADMIN "$work/foundation-registry.json" bearer
call 403 DELETE '/api/v1/admin/access/registry/roles/ADMIN?version=0' '' bearer
# Lọc đúng endpoint trên DB; registry lớn hơn một trang không làm ca kiểm metadata bỏ sót bản ghi.
call 200 GET '/api/v1/admin/access/resources?kind=ENDPOINT&query=PUT%20%2Fapi%2Fv1%2Fadmin%2Fcatalogs%2F%7Bid%7D&page=0&size=100' '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]["content"]; e=next(x for x in d if x["target"]=="PUT /api/v1/admin/catalogs/{id}"); assert e["group"]=="CATALOG" and "CATALOG_UPDATE" in e["declaredPermissions"] and not e["publicRoute"]' "$result"

# Role mặc định ngoài ADMIN sửa/tắt được, version cũ không ghi đè; xóa dọn mapping và giữ tombstone.
printf '%s\n' '{"nameVi":"Tài xế CI","nameEn":"CI driver","active":false,"version":0}' > "$work/foundation-registry.json"
call 200 PUT /api/v1/admin/access/registry/roles/DRIVER "$work/foundation-registry.json" bearer
printf '%s\n' '{"nameVi":"Tài xế CI","nameEn":"CI driver","active":true,"version":1}' > "$work/foundation-registry.json"
call 200 PUT /api/v1/admin/access/registry/roles/DRIVER "$work/foundation-registry.json" bearer
call 409 PUT /api/v1/admin/access/registry/roles/DRIVER "$work/foundation-registry.json" bearer
call 200 DELETE '/api/v1/admin/access/registry/roles/DRIVER?version=2' '' bearer
[[ "$(sql "SELECT deleted AND NOT active FROM auth_roles WHERE code='DRIVER'")" == t ]]
[[ "$(sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='DRIVER'")" == 0 ]]

printf '%s\n' '{"nameVi":"Biên tập","nameEn":"Editor","active":true,"version":0}' > "$work/foundation-registry.json"
call 201 POST /api/v1/admin/access/registry/roles/EDITOR_CI "$work/foundation-registry.json" bearer
printf '%s\n' '{"permissions":["CATALOG_VIEW","CATALOG_READ"],"version":0}' > "$work/foundation-permissions.json"
call 200 PUT /api/v1/admin/access/roles/EDITOR_CI/permissions "$work/foundation-permissions.json" bearer
account_version=$(sql "SELECT version FROM auth_users WHERE id=$account_id")
printf '{"roles":["CUSTOMER","EDITOR_CI"],"version":%s}\n' "$account_version" > "$work/foundation-roles.json"
call 200 PUT "/api/v1/admin/access/users/$account_id/roles" "$work/foundation-roles.json" bearer
call 409 DELETE '/api/v1/admin/access/registry/roles/EDITOR_CI?version=0' '' bearer
assert_code COM015
login_body accountci "$password_second"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
foundation_user_token="$token"
call 200 GET /admin '' bearer
call 403 GET /operations/access '' bearer
call 200 GET /api/v1/auth/me '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]; assert set(d["roles"])=={"CUSTOMER","EDITOR_CI"} and "CATALOG_READ" in d["permissions"]' "$result"
account_version=$(field data.version)

# Avatar giả chỉ là metadata trong DB CI, không upload/xóa Cloudinary; server vẫn kiểm owner/READY/version.
own_avatar=$(sql "INSERT INTO media_assets(public_id,status,url,thumbnail_url,format,bytes,width,height,created_by,updated_by) VALUES ('ci/avatar-own','READY','https://res.cloudinary.com/demo/avatar.png','https://res.cloudinary.com/demo/avatar-small.png','png',80,2,2,$account_id,$account_id) RETURNING id" | sed -n '1p')
printf '{"displayName":"CI profile","avatarMediaId":%s,"version":%s}\n' "$own_avatar" "$account_version" > "$work/foundation-profile.json"
call 200 PUT /api/v1/auth/profile "$work/foundation-profile.json" bearer
[[ "$(field data.avatarMediaId)" == "$own_avatar" ]]
call 409 PUT /api/v1/auth/profile "$work/foundation-profile.json" bearer
call 200 GET /account '' bearer
grep -q 'CI profile' "$result"
grep -q 'avatar-small.png' "$result"
if grep -q 'href="/login?lang=' "$result"; then exit 1; fi
call 200 GET '/account-access?lang=vi' '' bearer
call 200 GET /api/v1/auth/me '' bearer
account_version=$(field data.version)
printf '{"displayName":"CI profile","avatarMediaId":%s,"version":%s,"roles":["ADMIN"]}\n' "$own_avatar" "$account_version" > "$work/foundation-forged.json"
call 400 PUT /api/v1/auth/profile "$work/foundation-forged.json" bearer
foreign_avatar=$(sql "INSERT INTO media_assets(public_id,status,url,thumbnail_url,format,bytes,width,height,created_by,updated_by) VALUES ('ci/avatar-foreign','READY','https://res.cloudinary.com/demo/foreign.png','https://res.cloudinary.com/demo/foreign-small.png','png',80,2,2,1,1) RETURNING id" | sed -n '1p')
printf '{"displayName":"CI profile","avatarMediaId":%s,"version":%s}\n' "$foreign_avatar" "$account_version" > "$work/foundation-profile.json"
call 404 PUT /api/v1/auth/profile "$work/foundation-profile.json" bearer

token="$foundation_admin_token"
printf '{"version":0}\n' > "$work/foundation-delete-image.json"
call 409 DELETE "/api/v1/media/$own_avatar" "$work/foundation-delete-image.json" bearer
assert_code COM015
[[ "$(sql "SELECT status FROM media_assets WHERE id=$own_avatar")" == READY ]]

# Quyền thu hồi trong DB có hiệu lực với JWT hiện hành; role không phải danh sách hardcode.
printf '%s\n' '{"permissions":[],"version":1}' > "$work/foundation-permissions.json"
call 200 PUT /api/v1/admin/access/roles/EDITOR_CI/permissions "$work/foundation-permissions.json" bearer
token="$foundation_user_token"
call 403 GET /admin '' bearer
token="$foundation_admin_token"
printf '%s\n' '{"kind":"ENDPOINT","target":"GET /api/v1/media/{id}","nameVi":"Ảnh CI","nameEn":"CI image","enabled":true,"permissions":[],"roles":["EDITOR_CI"],"version":0}' > "$work/foundation-policy.json"
call 201 POST /api/v1/admin/access/policies "$work/foundation-policy.json" bearer
policy_id=$(python3 -c 'import json,sys; print(next(x["id"] for x in json.load(open(sys.argv[1]))["data"] if x["target"]=="GET /api/v1/media/{id}"))' "$result")
account_version=$(sql "SELECT version FROM auth_users WHERE id=$account_id")
printf '{"roles":["CUSTOMER"],"version":%s}\n' "$account_version" > "$work/foundation-roles.json"
call 200 PUT "/api/v1/admin/access/users/$account_id/roles" "$work/foundation-roles.json" bearer
call 409 DELETE '/api/v1/admin/access/registry/roles/EDITOR_CI?version=0' '' bearer
assert_code COM015
call 200 DELETE "/api/v1/admin/access/policies/$policy_id?version=0" '' bearer
call 200 DELETE '/api/v1/admin/access/registry/roles/EDITOR_CI?version=0' '' bearer

docker rm --force vietroute-auth-ci >/dev/null
start_app vietroute-auth-ci 18080 vietroute_ci 1000 '' vietroute true
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 "$base/health" >/dev/null
[[ "$(sql "SELECT deleted AND NOT active FROM auth_roles WHERE code='DRIVER'")" == t ]]
[[ "$(sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='DRIVER'")" == 0 ]]
printf 'Annotation discovery, role lifecycle, multi-role DB guards and avatar/profile checks passed.\n'
