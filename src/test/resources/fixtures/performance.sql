-- Chỉ BusinessIntegrationTest loopback/schema ngẫu nhiên. :trip/:booking là ID fixture server test, không HTTP.
INSERT INTO auth_data_scopes(code,name_vi,name_en)
SELECT 'PERF03S'||n,'Scope fixture '||n,'Scope fixture '||n FROM generate_series(1,20000) n;
INSERT INTO carriers(scope_id,code,name,phone)
SELECT id,'PERF03C'||substring(code FROM 8),
    CASE WHEN id%1000=0 THEN 'needlexyz ' ELSE 'Carrier fixture ' END||id,'0000000000'
FROM auth_data_scopes WHERE code LIKE 'PERF03S%';
INSERT INTO catalog_items(type,code,name_vi,name_en)
SELECT 'VEHICLE_TYPE','PERF03CAT'||n,
    CASE WHEN n%1000=0 THEN 'needlexyz ' ELSE 'Danh mục fixture ' END||n,'Catalog fixture '||n
FROM generate_series(1,20000) n;

INSERT INTO carrier_trips(carrier_id,scope_id,route_id,vehicle_id,driver_id,driver_user_id,code,name_vi,name_en,
    plate_number,vehicle_type_id,vehicle_type_name_vi,vehicle_type_name_en,driver_name,driver_phone,
    departure_at,end_at,capacity,status)
SELECT t.carrier_id,t.scope_id,t.route_id,t.vehicle_id,t.driver_id,t.driver_user_id,'PERF03T'||n,t.name_vi,t.name_en,
    t.plate_number,t.vehicle_type_id,t.vehicle_type_name_vi,t.vehicle_type_name_en,t.driver_name,t.driver_phone,
    TIMESTAMPTZ '2027-01-01 08:00:00+07'+(n%730)*INTERVAL '1 day',
    TIMESTAMPTZ '2027-01-02 08:00:00+07'+(n%730)*INTERVAL '1 day',t.capacity,
    CASE WHEN n%3=0 THEN 'CANCELLED' WHEN n%3=1 THEN 'OPEN' ELSE 'COMPLETED' END
FROM carrier_trips t CROSS JOIN generate_series(1,15000) n WHERE t.id IN(:trip,:otherTrip);
INSERT INTO trip_stops(trip_id,carrier_id,scope_id,location_id,province_id,stop_order,name_vi,name_en,address,latitude,longitude,
    scheduled_arrival_at,scheduled_departure_at,cumulative_fare,pickup_allowed,dropoff_allowed)
SELECT t.id,t.carrier_id,t.scope_id,s.location_id,s.province_id,s.stop_order,s.name_vi,s.name_en,s.address,s.latitude,s.longitude,
    t.departure_at+(s.stop_order-1)*INTERVAL '1 hour',t.departure_at+(s.stop_order-1)*INTERVAL '1 hour',
    s.cumulative_fare,s.pickup_allowed,s.dropoff_allowed
FROM carrier_trips t CROSS JOIN trip_stops s WHERE t.code LIKE 'PERF03T%'
    AND s.trip_id=CASE WHEN t.carrier_id=(SELECT carrier_id FROM carrier_trips WHERE id=:trip) THEN :trip ELSE :otherTrip END;
INSERT INTO bookings(trip_id,carrier_id,scope_id,source_code,source_id,pickup_stop_id,dropoff_stop_id,pickup_order,dropoff_order,
    seats,unit_price,total_price,contact_name,contact_phone,request_key,status,paid_amount,refunded_amount)
SELECT t.id,t.carrier_id,t.scope_id,'PHONE',b.source_id,p.id,d.id,1,3,1,b.unit_price,b.unit_price,
    'Contact fixture','0000000000',gen_random_uuid(),
    CASE WHEN n=1 THEN 'CONFIRMED' WHEN n=2 THEN 'CANCELLED' ELSE 'COMPLETED' END,
    CASE WHEN n=1 THEN 0 ELSE 100 END,CASE WHEN n=2 THEN 50 ELSE 0 END
FROM carrier_trips t JOIN trip_stops p ON p.trip_id=t.id AND p.stop_order=1
JOIN trip_stops d ON d.trip_id=t.id AND d.stop_order=3
CROSS JOIN bookings b CROSS JOIN generate_series(1,3) n
WHERE t.code LIKE 'PERF03T%' AND b.id=:booking;
ANALYZE auth_data_scopes;
ANALYZE carriers;
ANALYZE catalog_items;
ANALYZE carrier_trips;
ANALYZE trip_stops;
ANALYZE bookings;
-- Tương đương dọn pending list sau đợt nạp bulk; giữ fastupdate production mặc định, không ép planner.
SELECT pg_catalog.gin_clean_pending_list('idx_carriers_search_contact'::regclass);
SELECT pg_catalog.gin_clean_pending_list('idx_carriers_search_name'::regclass);
SELECT pg_catalog.gin_clean_pending_list('idx_catalog_search_text'::regclass);
