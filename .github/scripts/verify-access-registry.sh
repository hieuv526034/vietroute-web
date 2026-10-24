#!/usr/bin/env bash
# Chỉ source trong DB/container Docker tạm; kiểm quota khác 512 mà không dùng .env/Neon.
printf 'Registry stage: DB search/paging, configurable capacity, tombstones and concurrent final slot\n'
token="$tree_admin_token"
call 200 GET '/api/v1/admin/access/registry/roles/page?query=HISTORY_CI&active=false&size=1' '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert d["capacity"]==600 and d["allocated"]>d["page"]["totalElements"];assert d["page"]["totalElements"]==1 and not d["page"]["content"][0]["active"]' "$result"
call 200 GET '/api/v1/admin/access/registry/roles/page?query=HISTORY%25' '' bearer
[[ "$(field data.page.totalElements)" == 0 ]]
call 400 GET '/api/v1/admin/access/registry/roles/page?size=101' '' bearer
call 400 GET '/api/v1/admin/access/registry/roles/page?active=invalid' '' bearer
call 400 GET '/api/v1/admin/access/registry/invalid/page' '' bearer

# Bơm metadata giả trong DB tạm để kiểm API không âm thầm cắt ở 512. Không cấp quyền/user cho các role giả.
registry_fill=$(sql "SELECT 599-count(*) FROM auth_roles WHERE NOT deleted")
sql "INSERT INTO auth_roles(code,name_vi,name_en,created_by,updated_by) SELECT 'ZZ_CI_'||lpad(n::text,4,'0'),'Vai trò thử '||n,'Fixture role '||n,0,0 FROM generate_series(1,$registry_fill) n" >/dev/null
call 200 GET '/api/v1/admin/access/registry/roles' '' bearer
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert len(v)==599;assert any(x["code"].startswith("ZZ_CI_") for x in v)' "$result"
call 200 GET '/api/v1/admin/access/roles' '' bearer
python3 -c 'import json,sys;assert len(json.load(open(sys.argv[1]))["data"])==599' "$result"
call 200 GET '/api/v1/admin/access/registry/roles/page?query=Fixture%20role&size=10&page=1' '' bearer
python3 -c 'import json,sys;v=json.load(open(sys.argv[1]))["data"];assert v["page"]["page"]==1 and len(v["page"]["content"])==10 and v["page"]["totalElements"]==int(sys.argv[2]);assert v["allocated"]==599' "$result" "$registry_fill"

# Hai request tranh suất cuối: advisory lock kiểm quota + INSERT cùng transaction, chỉ một request được tạo.
printf '%s\n' '{"nameVi":"Suất cuối CI","nameEn":"Last slot CI","active":true,"version":0}' > "$work/registry-last.json"
registry_pids=()
for registry_code in FINAL_A_CI FINAL_B_CI; do
  curl --silent --show-error --max-time 20 -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data-binary "@$work/registry-last.json" --output "$work/$registry_code.json" --write-out '%{http_code}' \
    "$base/api/v1/admin/access/registry/roles/$registry_code" > "$work/$registry_code.status" &
  registry_pids+=("$!")
done
for registry_pid in "${registry_pids[@]}"; do wait "$registry_pid"; done
python3 -c 'import json,pathlib,sys;p=pathlib.Path(sys.argv[1]);statuses=[(p/(x+".status")).read_text() for x in ("FINAL_A_CI","FINAL_B_CI")];assert sorted(statuses)==["201","409"];errors=[json.loads((p/(x+".json")).read_text()) for x in ("FINAL_A_CI","FINAL_B_CI") if (p/(x+".status")).read_text()=="409"];assert errors[0]["code"]=="COM016"' "$work"
[[ "$(sql "SELECT count(*) FROM auth_roles WHERE NOT deleted")" == 600 ]]

# Xóa mềm không mất tombstone nhưng trả suất cho mã khác; mã cũ vẫn không được tái sử dụng.
call 200 DELETE '/api/v1/admin/access/registry/roles/ZZ_CI_0001?version=0' '' bearer
call 201 POST '/api/v1/admin/access/registry/roles/AFTER_DELETE_CI' "$work/registry-last.json" bearer
[[ "$(sql "SELECT deleted FROM auth_roles WHERE code='ZZ_CI_0001'")" == t ]]
[[ "$(sql "SELECT count(*) FROM auth_roles WHERE NOT deleted")" == 600 ]]
[[ "$(sql "SELECT count(*) FROM auth_roles")" -gt 600 ]]
call 409 POST '/api/v1/admin/access/registry/roles/OVER_CAPACITY_CI' "$work/registry-last.json" bearer
assert_code COM016
[[ "$(sql "SELECT count(*) FROM auth_roles WHERE code='OVER_CAPACITY_CI'")" == 0 ]]
printf 'Registry search/count/paging, complete legacy lists, quota lock and tombstone capacity checks passed.\n'
