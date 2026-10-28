#!/usr/bin/env bash
# Staging Docker cô lập: không nhận URL/DB tùy ý, không đọc .env hoặc phát mutation production.
[[ "${GITHUB_ACTIONS:-}" == true ]]
printf 'Business load stage: synthetic dataset, two HTTP nodes and bounded pool\n'
business_base="$base"
business_token="$token"
business_csrf="$csrf_token"
cp "$work/cookies" "$work/business-previous-cookies"
sql 'CREATE DATABASE vietroute_business_load_ci' >/dev/null
business_sql() { docker exec -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -X -U vietroute_ci -d vietroute_business_load_ci -v ON_ERROR_STOP=1 -Atc "$1"; }
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
CI_APPLICATION_POOL_SIZE=2 start_app vietroute-auth-ci 18080 vietroute_business_load_ci 5000
CI_APPLICATION_POOL_SIZE=2 start_app vietroute-auth-ci2 18081 vietroute_business_load_ci 5000
for business_port in 18080 18081; do
  curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 "http://127.0.0.1:$business_port/health" >/dev/null
done
base=http://127.0.0.1:18080
printf '' > "$work/cookies"
login_body admin "$admin_password"
call 200 POST /api/v1/auth/login "$work/login.json" cookie
token=$(field data.accessToken)
business_load_token="$token"
printf '{}\n' > "$work/business-demo.json"
call 200 POST /api/v1/admin/demo "$work/business-demo.json" bearer

# Seed cha qua API hiện hành; credential DRIVER ready là fixture CI, không ngoại lệ vận hành thật.
for business_number in 1 2; do
  if [[ "$business_number" == 1 ]]; then
    business_carrier_code=DEMOMIENNAM; business_vehicle_code=DEMOVEH01; business_driver_code=DEMODRIVER01
  else
    business_carrier_code=DEMOBACNAM; business_vehicle_code=DEMOVEH03; business_driver_code=DEMODRIVER03
  fi
  business_carrier=$(business_sql "SELECT id FROM carriers WHERE code='$business_carrier_code'")
  business_scope=$(business_sql "SELECT scope_id FROM carriers WHERE id=$business_carrier")
  business_vehicle=$(business_sql "SELECT id FROM carrier_vehicles WHERE code='$business_vehicle_code'")
  business_driver=$(business_sql "SELECT id FROM carrier_drivers WHERE code='$business_driver_code'")
  business_sql "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email,email_verified_at) SELECT 'perf03driver$business_number',password_hash,'Driver fixture',false,'perf03driver$business_number@example.invalid',CURRENT_TIMESTAMP FROM auth_users WHERE id=1" >/dev/null
  business_user=$(business_sql "SELECT id FROM auth_users WHERE username='perf03driver$business_number'")
  business_sql "INSERT INTO auth_user_roles(user_id,role_code) VALUES($business_user,'DRIVER'); INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES($business_scope,$business_user); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES($business_scope,$business_user,'DRIVER'); UPDATE carrier_drivers SET user_id=$business_user WHERE id=$business_driver" >/dev/null
  business_first=$(business_sql "SELECT id FROM location_places WHERE code='DEMOPLACEA'")
  business_middle=$(business_sql "SELECT id FROM location_places WHERE code='DEMOPLACEB'")
  business_last=$(business_sql "SELECT id FROM location_places WHERE code='DEMOPLACEC'")
  python3 - "$business_carrier" "$business_number" "$business_first" "$business_middle" "$business_last" > "$work/business-route.json" <<'PY'
import json,sys
carrier,number,first,middle,last=map(int,sys.argv[1:])
json.dump({"carrierId":carrier,"code":f"PERF03ROUTE{number}","nameVi":"Route fixture","nameEn":"Route fixture","description":"","active":True,"stops":[{"locationId":first,"arrivalOffsetMinutes":0,"departureOffsetMinutes":0,"cumulativeFare":0,"pickupAllowed":True,"dropoffAllowed":False},{"locationId":middle,"arrivalOffsetMinutes":60,"departureOffsetMinutes":65,"cumulativeFare":100000,"pickupAllowed":True,"dropoffAllowed":True},{"locationId":last,"arrivalOffsetMinutes":120,"departureOffsetMinutes":120,"cumulativeFare":250000,"pickupAllowed":False,"dropoffAllowed":True}]},sys.stdout)
PY
  call 201 POST /api/v1/admin/routes "$work/business-route.json" bearer
  business_route=$(field data.id)
  python3 - "$business_number" "$business_route" "$business_vehicle" "$business_driver" > "$work/business-trip.json" <<'PY'
import datetime,json,sys
number,route,vehicle,driver=map(int,sys.argv[1:])
start=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=number+10)
json.dump({"code":f"PERF03BASE{number}","routeId":route,"vehicleId":vehicle,"driverId":driver,"capacity":10,"departureAt":start.isoformat()},sys.stdout)
PY
  call 201 POST /api/v1/admin/trips "$work/business-trip.json" bearer
  business_trip=$(field data.id)
  printf '{"status":"OPEN","version":0,"note":"Fixture"}\n' > "$work/business-open.json"
  call 200 PUT "/api/v1/admin/trips/$business_trip/state" "$work/business-open.json" bearer
  if [[ "$business_number" == 1 ]]; then business_trip1="$business_trip"; else business_trip2="$business_trip"; fi
done
business_pickup=$(business_sql "SELECT id FROM trip_stops WHERE trip_id=$business_trip1 AND stop_order=1")
business_dropoff=$(business_sql "SELECT id FROM trip_stops WHERE trip_id=$business_trip1 AND stop_order=3")
python3 - "$business_trip1" "$business_pickup" "$business_dropoff" > "$work/business-booking.json" <<'PY'
import json,sys,uuid
trip,pickup,dropoff=map(int,sys.argv[1:])
json.dump({"tripId":trip,"pickupStopId":pickup,"dropoffStopId":dropoff,"seats":1,"contactName":"Contact fixture","contactPhone":"0000000000","requestKey":str(uuid.uuid4())},sys.stdout)
PY
call 201 POST /api/v1/admin/bookings "$work/business-booking.json" bearer
business_booking=$(field data.id)
python3 - "$business_trip1" "$business_trip2" "$business_booking" > "$work/business-fixture.sql" <<'PY'
import pathlib,sys
trip,other,booking=map(int,sys.argv[1:])
value=pathlib.Path('src/test/resources/fixtures/performance.sql').read_text(encoding='utf-8')
print(value.replace(':otherTrip',str(other)).replace(':trip',str(trip)).replace(':booking',str(booking)))
PY
docker exec -i -e PGOPTIONS=-csearch_path=vietroute vietroute-db-ci psql -X -U vietroute_ci -d vietroute_business_load_ci -v ON_ERROR_STOP=1 < "$work/business-fixture.sql" >/dev/null
[[ "$(business_sql "SELECT count(*) FROM carrier_trips WHERE code LIKE 'PERF03T%'")" == 30000 ]]
[[ "$(business_sql "SELECT count(*) FROM bookings b JOIN carrier_trips t ON t.id=b.trip_id WHERE t.code LIKE 'PERF03T%'")" == 90000 ]]
CI_BUSINESS_LOAD_TOKEN="$business_load_token" CI_BUSINESS_METRICS_TOKEN="$metrics_token" python3 src/test/resources/dev/business-load.py | tee "$work/business-http-load.json"
# Trả node/cookie về DB và flags trước stage; không phục hồi DRIVER tombstone hoặc đổi dữ liệu ca quyền trước đó.
docker rm --force vietroute-auth-ci vietroute-auth-ci2 >/dev/null
start_app vietroute-auth-ci 18080 vietroute_ci 300 '' vietroute true
start_app vietroute-auth-ci2 18081
for business_port in 18080 18081; do
  curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors --retry-connrefused --max-time 5 "http://127.0.0.1:$business_port/health" >/dev/null
done
cp "$work/business-previous-cookies" "$work/cookies"
csrf_token="$business_csrf"
base="$business_base"
token="$business_token"
printf 'Business HTTP workload passed; synthetic Docker staging, not production capacity.\n'
