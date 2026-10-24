#!/usr/bin/env bash
# Chỉ source trong verify-auth.sh trên DB/container CI; không đọc .env, không seed dữ liệu địa lý production.
printf 'Location stage: public lookup, admin guards, coordinates, references and status/version transactions\n'
token="$tree_admin_token"
[[ "$(sql "SELECT count(*) FROM auth_permissions WHERE code IN ('LOCATION_VIEW','LOCATION_READ','LOCATION_CREATE','LOCATION_UPDATE') AND active")" == 4 ]]
[[ "$(sql "SELECT count(*) FROM auth_role_permissions WHERE role_code='ADMIN' AND permission_code LIKE 'LOCATION_%'")" == 4 ]]
call 200 GET '/api/v1/admin/access/registry/permissions/page?query=LOCATION_' '' bearer
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"]["page"];assert v["totalElements"]==4;assert all(x["builtIn"] and x["nameVi"]!=x["code"] and x["nameEn"]!=x["code"] for x in v["content"])' "$result"
printf '%s\n' '{"nameVi":"Không được tắt","nameEn":"Must stay active","active":false,"version":0}' > "$work/location-disable-permission.json"
call 403 PUT '/api/v1/admin/access/registry/permissions/LOCATION_READ' "$work/location-disable-permission.json" bearer
assert_code AUTH003

for location_number in 1 2; do
  python3 -c 'import json,sys;n=sys.argv[1];json.dump({"code":"PROVINCECI0"+n,"nameVi":"Tỉnh CI "+n,"nameEn":"CI province "+n,"sortOrder":int(n),"active":True},sys.stdout)' "$location_number" > "$work/location-province-$location_number.json"
  call 201 POST /api/v1/admin/provinces "$work/location-province-$location_number.json" bearer
  if [[ "$location_number" == 1 ]]; then location_province1=$(field data.id); else location_province2=$(field data.id); fi
  python3 -c 'import json,sys;n=sys.argv[1];p=int(sys.argv[2]);json.dump({"code":"LOCATIONCI0"+n,"provinceId":p,"nameVi":"Địa điểm CI "+n,"nameEn":"CI place "+n,"address":"Địa chỉ CI "+n,"latitude":10.123456 if n=="1" else -90,"longitude":106.123456 if n=="1" else 180,"active":True},sys.stdout)' "$location_number" "$(field data.id)" > "$work/location-place-$location_number.json"
  call 201 POST /api/v1/admin/locations "$work/location-place-$location_number.json" bearer
  if [[ "$location_number" == 1 ]]; then location_place1=$(field data.id); else location_place2=$(field data.id); fi
  python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert v["createdBy"]==1 and v["updatedBy"]==1 and v["version"]==0 and v["provinceActive"]' "$result"
done
call 409 POST /api/v1/admin/provinces "$work/location-province-1.json" bearer
assert_code COM005
call 409 POST /api/v1/admin/locations "$work/location-place-1.json" bearer
assert_code COM005
call 200 GET '/api/v1/locations?query=LOCATIONCI&size=1&page=1'
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert v["totalElements"]==2 and v["page"]==1 and len(v["content"])==1;assert v["content"][0]["name"]=="CI place 2";assert not {"createdBy","updatedBy","version","active","provinceActive","nameVi"}.intersection(v["content"][0])' "$result"
call 200 GET '/api/v1/locations?query=%25_%27%20OR%20true%20--'
[[ "$(field data.totalElements)" == 0 ]]
call 400 GET '/api/v1/locations?provinceId=0'
call 400 GET '/api/v1/provinces?size=101'
call 404 GET '/api/v1/admin/locations/999999999' '' bearer

for location_invalid in latitude longitude precision provinceId audit; do
  python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));k=sys.argv[2];v.update({"latitude":91} if k=="latitude" else {"longitude":181} if k=="longitude" else {"latitude":10.1234567} if k=="precision" else {"provinceId":0} if k=="provinceId" else {"createdBy":0});json.dump(v,sys.stdout)' "$work/location-place-1.json" "$location_invalid" > "$work/location-invalid.json"
  call 400 POST /api/v1/admin/locations "$work/location-invalid.json" bearer
done

# Tạo tham chiếu thật trên DB tạm để chứng minh ngừng sử dụng không làm mất ID đang được nghiệp vụ sử dụng.
sql "CREATE TABLE ci_location_links(id BIGINT PRIMARY KEY,location_id BIGINT NOT NULL REFERENCES location_places(id)); INSERT INTO ci_location_links VALUES (1,$location_place1)" >/dev/null
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.pop("code");v.update(active=False,version=0);json.dump(v,sys.stdout)' "$work/location-province-1.json" > "$work/location-province-update.json"
call 200 PUT "/api/v1/admin/provinces/$location_province1" "$work/location-province-update.json" bearer
call 200 GET "/api/v1/locations?provinceId=$location_province1&active=true"
[[ "$(field data.totalElements)" == 0 ]]
call 200 GET "/api/v1/admin/locations/$location_place1" '' bearer
[[ "$(field data.provinceActive)" == False && "$(field data.active)" == True ]]
call 409 POST /api/v1/admin/locations "$work/location-place-1.json" bearer
assert_code LOC001
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.pop("code");v.pop("provinceId");v.update(active=False,version=0);json.dump(v,sys.stdout)' "$work/location-place-1.json" > "$work/location-place-update.json"
call 200 PUT "/api/v1/admin/locations/$location_place1" "$work/location-place-update.json" bearer
[[ "$(sql "SELECT count(*) FROM ci_location_links c JOIN location_places l ON l.id=c.location_id WHERE l.id=$location_place1 AND NOT l.active")" == 1 ]]
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.update(active=True,version=1);json.dump(v,sys.stdout)' "$work/location-place-update.json" > "$work/location-place-enable.json"
call 409 PUT "/api/v1/admin/locations/$location_place1" "$work/location-place-enable.json" bearer
assert_code LOC001
[[ "$(sql "SELECT version FROM location_places WHERE id=$location_place1")" == 1 ]]
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.update(active=True,version=1);json.dump(v,sys.stdout)' "$work/location-province-update.json" > "$work/location-province-enable.json"
call 200 PUT "/api/v1/admin/provinces/$location_province1" "$work/location-province-enable.json" bearer
call 200 GET "/api/v1/locations?provinceId=$location_province1"
[[ "$(field data.totalElements)" == 0 ]]
call 200 PUT "/api/v1/admin/locations/$location_place1" "$work/location-place-enable.json" bearer
call 409 PUT "/api/v1/admin/locations/$location_place1" "$work/location-place-enable.json" bearer
assert_code COM008
call 200 GET "/api/v1/locations?provinceId=$location_province1"
[[ "$(field data.totalElements)" == 1 ]]
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.update(provinceId=int(sys.argv[2]),version=2);json.dump(v,sys.stdout)' "$work/location-place-enable.json" "$location_province2" > "$work/location-place-move.json"
call 400 PUT "/api/v1/admin/locations/$location_place1" "$work/location-place-move.json" bearer
call 405 DELETE "/api/v1/admin/locations/$location_place1" '' bearer
sql 'DROP TABLE ci_location_links' >/dev/null

# Hai request cùng version chỉ có một UPDATE thành công; request thua không ghi đè hoặc tăng version lần nữa.
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]));v.pop("code");v.pop("provinceId");v.update(active=False,version=0);json.dump(v,sys.stdout)' "$work/location-place-2.json" > "$work/location-race.json"
location_pids=()
for location_attempt in 1 2; do
  curl --silent --show-error --max-time 20 -X PUT -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data-binary "@$work/location-race.json" --output "$work/location-race-$location_attempt.json" --write-out '%{http_code}' \
    "$base/api/v1/admin/locations/$location_place2" > "$work/location-race-$location_attempt.status" &
  location_pids+=("$!")
done
for location_pid in "${location_pids[@]}"; do wait "$location_pid"; done
python3 -c 'import pathlib,sys;p=pathlib.Path(sys.argv[1]);assert sorted((p/("location-race-"+str(n)+".status")).read_text() for n in (1,2))==["200","409"]' "$work"
[[ "$(sql "SELECT version FROM location_places WHERE id=$location_place2")" == 1 ]]
if sql "UPDATE location_places SET latitude=91 WHERE id=$location_place2" >/dev/null 2>&1; then exit 1; fi
if sql "UPDATE location_places SET longitude=181 WHERE id=$location_place2" >/dev/null 2>&1; then exit 1; fi

# Có đủ quyền mới nhưng không có ADMIN vẫn bị chặn cả page/read/create/update, không chỉ ẩn menu.
sql "INSERT INTO auth_roles(code,name_vi,name_en) VALUES ('LOCATION_CI','Quyền địa điểm CI','CI location permissions'); INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'LOCATION_CI',code FROM auth_permissions WHERE code LIKE 'LOCATION_%'; INSERT INTO auth_users(username,password_hash,display_name,active,must_change_password,email_verified_at) SELECT 'locationstaffci',password_hash,'Location CI',true,false,CURRENT_TIMESTAMP FROM auth_users WHERE id=1; INSERT INTO auth_user_roles(user_id,role_code) SELECT id,'LOCATION_CI' FROM auth_users WHERE username='locationstaffci'" >/dev/null
login_body locationstaffci "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
call 403 GET /operations/locations '' bearer
call 403 GET /api/v1/admin/locations '' bearer
call 403 POST /api/v1/admin/locations "$work/location-place-2.json" bearer
call 403 PUT "/api/v1/admin/locations/$location_place2" "$work/location-place-enable.json" bearer
token="$tree_admin_token"
printf 'Locations passed: source permissions/names, admin-only URLs, public projection/filter/paging, coordinates, parent status, immutable references, stale versions and retained IDs.\n'
