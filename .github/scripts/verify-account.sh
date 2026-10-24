#!/usr/bin/env bash
# Được source sau verify-auth.sh, dùng đúng DB container tạm và secret CI; không đọc .env/Neon.
# SMTP trỏ loopback:1 không có server, chứng minh outbox retry khi mail lỗi mà không gửi thư thật.
printf 'Account lifecycle stage: encrypted outbox, activation, reset, password history and request actor\n'
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
start_app vietroute-auth-ci 18080 vietroute_ci 1000 '' vietroute true
for attempt in {1..40}; do
  if curl --fail --silent --max-time 3 "$base/health" >/dev/null; then break; fi
  sleep 1
done
: > "$work/cookies"
token=''
call 200 GET /api/v1/auth/options '' none
[[ "$(field data.emailFlowsEnabled)" == True ]]
# Giữ email gốc khi bản ghi bootstrap bị mất; phản hồi public vẫn chung 202.
CI_PASSWORD="$admin_password" python3 -c 'import json,os,sys;json.dump({"username":"bootstrapclaimci","displayName":"Reserved CI","email":"bootstrap-ci@example.invalid","password":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/reserved-email.json"
call 202 POST /api/v1/auth/register "$work/reserved-email.json" cookie
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='bootstrapclaimci'")" == 0 ]]

account_password="$admin_password"
CI_PASSWORD="$account_password" python3 -c 'import json,os,sys; json.dump({"username":"accountci","displayName":"CI account","email":"accountci@example.invalid","password":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/register.json"
call 403 POST /api/v1/auth/register "$work/register.json" none
call 202 POST /api/v1/auth/register "$work/register.json" cookie
account_id=$(sql "SELECT id FROM auth_users WHERE username='accountci'")
[[ -n "$account_id" ]]
[[ "$(sql "SELECT active FROM auth_users WHERE id=$account_id")" == f ]]
[[ "$(sql "SELECT role_code FROM auth_user_roles WHERE user_id=$account_id")" == CUSTOMER ]]
[[ "$(sql "SELECT count(*) FROM auth_account_challenges WHERE user_id=$account_id AND purpose='ACTIVATE'")" == 1 ]]
call 202 POST /api/v1/auth/register "$work/register.json" cookie
[[ "$(sql "SELECT count(*) FROM auth_users WHERE username='accountci'")" == 1 ]]
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); d["roles"]=["ADMIN"]; json.dump(d,sys.stdout)' "$work/register.json" > "$work/forged-register.json"
call 400 POST /api/v1/auth/register "$work/forged-register.json" cookie
login_body accountci "$account_password"
call 401 POST /api/v1/auth/login "$work/login.json" cookie

# Dùng production crypto/DTO để đọc code thật từ encrypted outbox CI; mã chỉ ghi file riêng, không in log.
unzip -q "$RUNNER_TEMP/production.war" -d "$work/account-war"
account_cp="$work/account-war/WEB-INF/classes:$work/account-war/WEB-INF/lib/*"
javac -proc:none -encoding UTF-8 -cp "$account_cp" -d "$work/account-tools" src/test/resources/dev/AccountMailInspector.java
account_code() {
  sql "SELECT payload::text FROM system_jobs WHERE type='AUTH_ACCOUNT_MAIL' AND dedup_key LIKE 'account-mail:$account_id:%' ORDER BY created_at DESC,id DESC LIMIT 1" > "$work/account-mail.json"
  VIETROUTE_ENV=development VIETROUTE_DB_INIT_ENABLED=false VIETROUTE_JWT_SECRET="$jwt_secret" java -cp "$work/account-tools:$account_cp" AccountMailInspector "$work/account-mail.json" "$work/account-code.txt"
}
account_body() {
  CI_PASSWORD="${1:-}" python3 -c 'import json,os,sys; token=open(sys.argv[1]).read(); d={"email":"accountci@example.invalid","token":token}; d.update({"newPassword":os.environ["CI_PASSWORD"]} if os.environ["CI_PASSWORD"] else {}); json.dump(d,sys.stdout)' "$work/account-code.txt" > "$work/account-body.json"
}
account_code
account_body
call 200 POST /api/v1/auth/activate "$work/account-body.json" cookie
call 400 POST /api/v1/auth/activate "$work/account-body.json" cookie
assert_code AUTH010
[[ "$(sql "SELECT email_verified_at IS NOT NULL AND active AND NOT activation_pending FROM auth_users WHERE id=$account_id")" == t ]]
login_body accountci "$account_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
account_token=$(field data.accessToken)
account_trace=$(field traceId)
token="$account_token"
call 403 GET /api/v1/admin/access/resources '' bearer

account_change() {
  CI_CURRENT="$1" CI_NEW="$2" python3 -c 'import json,os,sys; json.dump({"currentPassword":os.environ["CI_CURRENT"],"newPassword":os.environ["CI_NEW"]},sys.stdout)' > "$work/account-change.json"
  call "$3" POST /api/v1/auth/change-password "$work/account-change.json" bearer
  if [[ "$3" == 200 ]]; then token=$(field data.accessToken); else assert_code AUTH008; fi
}
password_second="$next_password"
password_third="CiC3!$(openssl rand -hex 16)"
password_fourth="CiD4!$(openssl rand -hex 16)"
printf '::add-mask::%s\n' "$password_third" "$password_fourth"
account_change "$account_password" "$password_second" 200
account_change "$password_second" "$password_third" 200
account_change "$password_third" "$account_password" 400
account_change "$password_third" "$password_second" 400
account_change "$password_third" "$password_fourth" 200
account_change "$password_fourth" "$account_password" 200
[[ "$(sql "SELECT count(*) FROM auth_password_history WHERE user_id=$account_id")" == 2 ]]

printf '%s\n' '{"email":"accountci@example.invalid"}' > "$work/account-email.json"
call 202 POST /api/v1/auth/password/forgot "$work/account-email.json" cookie
account_code
account_body "$password_second"
old_account_token="$token"
call 200 POST /api/v1/auth/password/reset "$work/account-body.json" cookie
call 400 POST /api/v1/auth/password/reset "$work/account-body.json" cookie
assert_code AUTH010
token="$old_account_token"
call 401 GET /api/v1/auth/me '' bearer
login_body accountci "$password_second"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)

# Chống spam email theo cooldown; reset cũ bị tiêu thụ, lần mới hợp lệ rồi không enqueue lần liên tiếp.
call 202 POST /api/v1/auth/password/forgot "$work/account-email.json" cookie
mail_count=$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key LIKE 'account-mail:$account_id:%'")
call 202 POST /api/v1/auth/password/forgot "$work/account-email.json" cookie
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE dedup_key LIKE 'account-mail:$account_id:%'")" == "$mail_count" ]]
account_code
account_body "$password_third"
sql "UPDATE auth_account_challenges SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE user_id=$account_id AND purpose='RESET'" >/dev/null
call 400 POST /api/v1/auth/password/reset "$work/account-body.json" cookie
assert_code AUTH010

# Provider loopback lỗi không rollback đăng ký; worker ghi lỗi đã lọc và giữ retry/DEAD, không giả báo gửi thành công.
for attempt in {1..20}; do
  if [[ "$(sql "SELECT count(*) FROM system_jobs WHERE type='AUTH_ACCOUNT_MAIL' AND attempts>0 AND last_error_code<>''")" -gt 0 ]]; then break; fi
  sleep 1
done
[[ "$(sql "SELECT count(*) FROM system_jobs WHERE type='AUTH_ACCOUNT_MAIL' AND attempts>0 AND status<>'SUCCEEDED'")" -gt 0 ]]

login_body admin "$next_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
CI_PASSWORD="$next_password" python3 -c 'import json,os,sys;json.dump({"email":"replacement-ci@example.invalid","currentPassword":os.environ["CI_PASSWORD"]},sys.stdout)' > "$work/bootstrap-email.json"
call 403 POST /api/v1/auth/email "$work/bootstrap-email.json" bearer
assert_code AUTH003
call 200 GET "/api/v1/admin/system/api-journal?traceId=$account_trace&userId=$account_id" '' bearer
python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))["data"]["records"]; assert d["totalElements"]==1; e=d["content"][0]; assert e["username"]=="accountci" and e["userId"]==int(sys.argv[2]); assert "email" not in e' "$result" "$account_id"
call 200 GET "/api/v1/admin/system/api-journal?traceId=$account_trace&userId=1" '' bearer
[[ "$(field data.records.totalElements)" == 0 ]]
printf 'Account activation, reset, history, encryption and journal actor checks passed without real email.\n'
