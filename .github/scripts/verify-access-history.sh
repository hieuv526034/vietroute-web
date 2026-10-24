#!/usr/bin/env bash
# Chỉ source từ verify-auth.sh: PostgreSQL/container tạm, không đọc .env hoặc chạm Neon.
printf 'Access history stage: committed deltas, verified actor, rollback and bounded cleanup\n'
token="$tree_admin_token"
printf '%s\n' '{"nameVi":"Lịch sử CI","nameEn":"History CI","active":true,"version":0}' > "$work/history-registry.json"
call 201 POST /api/v1/admin/access/registry/roles/HISTORY_CI "$work/history-registry.json" bearer
printf '%s\n' '{"permissions":["CATALOG_READ"],"version":0}' > "$work/history-grants.json"
call 200 PUT /api/v1/admin/access/roles/HISTORY_CI/permissions "$work/history-grants.json" bearer
printf '%s\n' '{"permissions":["CATALOG_CREATE"],"version":1}' > "$work/history-grants.json"
call 200 PUT /api/v1/admin/access/roles/HISTORY_CI/permissions "$work/history-grants.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_access_changes WHERE event_type='ROLE_PERMISSIONS' AND target_key='HISTORY_CI' AND actor_id=1 AND created_by=1 AND updated_by=1")" == 2 ]]
[[ "$(sql "SELECT before_state->'permissions'='[\"CATALOG_READ\"]'::jsonb AND after_state->'permissions'='[\"CATALOG_CREATE\"]'::jsonb AND before_state->>'mappingCount'='1' AND after_state->>'mappingCount'='1' AND trace_id IS NOT NULL FROM auth_access_changes WHERE event_type='ROLE_PERMISSIONS' AND target_key='HISTORY_CI' ORDER BY id DESC LIMIT 1")" == t ]]
call 409 PUT /api/v1/admin/access/roles/HISTORY_CI/permissions "$work/history-grants.json" bearer
[[ "$(sql "SELECT count(*) FROM auth_access_changes WHERE event_type='ROLE_PERMISSIONS' AND target_key='HISTORY_CI'")" == 2 ]]

# Ghi audit bị từ chối: mapping và optimistic version đã cập nhật trước đó phải rollback cùng transaction.
cat > "$work/history-refuse.sql" <<'SQL'
CREATE FUNCTION ci_access_history_failure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.target_key='HISTORY_CI' AND NEW.event_type='ROLE_PERMISSIONS' THEN
        RAISE EXCEPTION 'CI only refused history' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ci_access_history_failure BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION ci_access_history_failure();
SQL
docker exec -i -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -U vietroute_ci -d vietroute_ci -v ON_ERROR_STOP=1 < "$work/history-refuse.sql" >/dev/null
printf '%s\n' '{"permissions":["CATALOG_DELETE"],"version":2}' > "$work/history-grants.json"
call 503 PUT /api/v1/admin/access/roles/HISTORY_CI/permissions "$work/history-grants.json" bearer
assert_code COM007
[[ "$(sql "SELECT permission_version FROM auth_roles WHERE code='HISTORY_CI'")" == 2 ]]
[[ "$(sql "SELECT permission_code FROM auth_role_permissions WHERE role_code='HISTORY_CI'")" == CATALOG_CREATE ]]
[[ "$(sql "SELECT count(*) FROM auth_access_changes WHERE event_type='ROLE_PERMISSIONS' AND target_key='HISTORY_CI'")" == 2 ]]
sql 'DROP TRIGGER ci_access_history_failure ON auth_access_changes; DROP FUNCTION ci_access_history_failure()' >/dev/null
call 200 PUT /api/v1/admin/access/roles/HISTORY_CI/permissions "$work/history-grants.json" bearer

# Role đang tắt vẫn phải xuất hiện trong phần bị bỏ của lịch sử gán user.
CI_ACCOUNT_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"historyuser","displayName":"History user CI","password":os.environ["CI_ACCOUNT_PASSWORD"],"roles":["HISTORY_CI"]},sys.stdout)' > "$work/history-user.json"
call 201 POST /api/v1/admin/accounts "$work/history-user.json" bearer
history_user=$(field data.id)
history_user_version=$(field data.version)
printf '%s\n' '{"nameVi":"Lịch sử CI","nameEn":"History CI","active":false,"version":0}' > "$work/history-registry.json"
call 200 PUT /api/v1/admin/access/registry/roles/HISTORY_CI "$work/history-registry.json" bearer
printf '{"roles":["CUSTOMER"],"version":%s}\n' "$history_user_version" > "$work/history-roles.json"
call 200 PUT "/api/v1/admin/access/users/$history_user/roles" "$work/history-roles.json" bearer
[[ "$(sql "SELECT before_state->'roles'='[\"HISTORY_CI\"]'::jsonb AND after_state->'roles'='[\"CUSTOMER\"]'::jsonb FROM auth_access_changes WHERE event_type='USER_ROLES' AND target_key='$history_user' ORDER BY id DESC LIMIT 1")" == t ]]
history_user_version=$(field data.version)
printf '{"active":false,"version":%s}\n' "$history_user_version" > "$work/history-status.json"
call 200 PUT "/api/v1/admin/accounts/$history_user/state" "$work/history-status.json" bearer
[[ "$(sql "SELECT before_state->>'active'='true' AND after_state->>'active'='false' FROM auth_access_changes WHERE event_type='ACCOUNT_STATUS' AND target_key='$history_user' ORDER BY id DESC LIMIT 1")" == t ]]

call 200 GET '/api/v1/admin/access/history?event=ROLE_PERMISSIONS&target=HISTORY_CI&actorId=1&size=1' '' bearer
python3 -c 'import json,sys;d=json.load(open(sys.argv[1]))["data"];assert d["totalElements"]==3 and len(d["content"])==1;v=d["content"][0];assert v["actorId"]==1 and v["actorUsername"]=="admin" and v["before"]["delta"];assert not any(x in json.dumps(v).lower() for x in ("password","accesstoken","authorization","email"))' "$result"
call 200 GET '/api/v1/admin/access/history?target=HISTORY%25' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
call 400 GET '/api/v1/admin/access/history?actorId=-1' '' bearer
call 400 GET '/api/v1/admin/access/history?event=INVALID' '' bearer

# Snapshot xóa role phải giữ tập quyền sắp bị dọn.
printf '%s\n' '{"nameVi":"Role xóa CI","nameEn":"Deleted role CI","active":true,"version":0}' > "$work/history-delete.json"
call 201 POST /api/v1/admin/access/registry/roles/HISTORY_DELETE_CI "$work/history-delete.json" bearer
printf '%s\n' '{"permissions":["CATALOG_READ"],"version":0}' > "$work/history-grants.json"
call 200 PUT /api/v1/admin/access/roles/HISTORY_DELETE_CI/permissions "$work/history-grants.json" bearer
call 200 DELETE '/api/v1/admin/access/registry/roles/HISTORY_DELETE_CI?version=0' '' bearer
[[ "$(sql "SELECT before_state->'permissions'='[\"CATALOG_READ\"]'::jsonb AND after_state->>'deleted'='true' FROM auth_access_changes WHERE event_type='ROLE_DELETED' AND target_key='HISTORY_DELETE_CI' ORDER BY id DESC LIMIT 1")" == t ]]

# Chỉ có ACCESS_READ qua role thường vẫn không được xem lịch sử nhạy cảm.
call 201 POST /api/v1/admin/access/registry/roles/HISTORY_READER_CI "$work/history-delete.json" bearer
printf '%s\n' '{"permissions":["ACCESS_READ"],"version":0}' > "$work/history-grants.json"
call 200 PUT /api/v1/admin/access/roles/HISTORY_READER_CI/permissions "$work/history-grants.json" bearer
CI_ACCOUNT_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"historyreader","displayName":"History reader CI","password":os.environ["CI_ACCOUNT_PASSWORD"],"roles":["HISTORY_READER_CI"]},sys.stdout)' > "$work/history-user.json"
call 201 POST /api/v1/admin/accounts "$work/history-user.json" bearer
login_body historyreader "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
CI_OLD="$admin_password" CI_NEW="$next_password" python3 -c 'import json,os,sys;json.dump({"currentPassword":os.environ["CI_OLD"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/history-password.json"
call 200 POST /api/v1/auth/change-password "$work/history-password.json" bearer
token=$(field data.accessToken)
call 403 GET /api/v1/admin/access/history '' bearer
assert_code AUTH012
CI_PASSWORD="$next_password" python3 -c 'import json,os,sys;json.dump({"email":"historyreader@example.invalid","currentPassword":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/history-email.json"
call 202 POST /api/v1/auth/email "$work/history-email.json" bearer
history_saved_account_id="$account_id"
account_id=$(sql "SELECT id FROM auth_users WHERE username='historyreader'")
account_code
account_id="$history_saved_account_id"
python3 -c 'import json,sys;json.dump({"email":"historyreader@example.invalid","token":open(sys.argv[1]).read()},sys.stdout)' "$work/account-code.txt" > "$work/history-activate.json"
call 200 POST /api/v1/auth/activate "$work/history-activate.json" cookie
call 403 GET /api/v1/admin/access/history '' bearer
assert_code AUTH003
token="$tree_admin_token"

# Retention không trả dòng quá hạn; cleanup thật xóa theo lô nhỏ và giới hạn mục tiêu.
sql "INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,before_state,after_state,created_at,created_by,updated_by) SELECT 'ROLE_UPDATED','ROLE','EXPIRED_HISTORY_CI',1,before_state,after_state,CURRENT_TIMESTAMP-INTERVAL '8 days',1,1 FROM auth_access_changes LIMIT 1" >/dev/null
call 200 GET '/api/v1/admin/access/history?target=EXPIRED_HISTORY_CI' '' bearer
[[ "$(field data.totalElements)" == 0 ]]
sql "INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,before_state,after_state,created_by,updated_by) SELECT 'ROLE_UPDATED','ROLE','CAPACITY_HISTORY_CI',1,source.before_state,source.after_state,1,1 FROM (SELECT before_state,after_state FROM auth_access_changes LIMIT 1) source CROSS JOIN generate_series(1,1005)" >/dev/null
for history_cleanup_attempt in {1..20}; do
  history_registry_version=$(sql "SELECT registry_version FROM auth_roles WHERE code='HISTORY_CI'")
  printf '{"nameVi":"Dọn lịch sử CI","nameEn":"History cleanup CI","active":false,"version":%s}\n' "$history_registry_version" > "$work/history-registry.json"
  call 200 PUT /api/v1/admin/access/registry/roles/HISTORY_CI "$work/history-registry.json" bearer
  if [[ "$(sql 'SELECT count(*) FROM auth_access_changes')" -le 1000 ]]; then break; fi
done
[[ "$(sql 'SELECT count(*) FROM auth_access_changes')" -le 1000 ]]
[[ "$(sql "SELECT count(*) FROM auth_access_changes WHERE target_key='EXPIRED_HISTORY_CI'")" == 0 ]]
printf 'Access history deltas, actor, rollback, ADMIN-only, retention and capacity checks passed.\n'
