package vn.vietroute.business.bootstrap.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.bootstrap.dto.response.BusinessDataResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Dữ liệu khởi tạo theo tập từ JSON phiên bản trong source; giữ dữ liệu có sẵn, không query/save theo từng dòng. */
@Repository
@RequiredArgsConstructor
public class BusinessDataRepo {

    private final ObjectMapper json;
    private final AppSettings settings;

    /** Một transaction do caller sở hữu, advisory lock serialize mọi lần seed và kiểm quota phạm vi. */
    public BusinessDataResponse seed(Connection connection, long actor) throws SQLException {
        return seed(connection, actor, load("bootstrap/foundation.json"));
    }

    /** Đọc bộ dữ liệu đóng gói theo tên resource cố định của server, không nhận đường dẫn từ HTTP. */
    private JsonNode load(String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return json.readTree(input);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** Bộ mở rộng chỉ dùng startup opt-in; API nền vẫn có phạm vi nhỏ, không tự cấp tài khoản. */
    public void seedAdditionalFoundation(Connection connection) throws SQLException {
        seed(connection, 0, load("bootstrap/operations.json"));
    }

    private BusinessDataResponse seed(Connection connection, long actor, JsonNode data) throws SQLException {
        try (
            PreparedStatement lock = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(781205611),pg_advisory_xact_lock(781205690)"
            )
        ) {
            lock.execute();
        }
        // Cùng mã nhưng đơn vị khác phải dừng thay vì ghép xe/tài xế vào nhầm nhà xe.
        String carriers = data.path("carriers").toString();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT (
                    SELECT count(*)
                    FROM carriers
                ) + (
                    SELECT count(*)
                    FROM jsonb_to_recordset(?::jsonb) x(code text, name text)
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM carriers c
                        WHERE c.code = x.code
                    )
                ), EXISTS (
                    SELECT 1
                    FROM jsonb_to_recordset(?::jsonb) x(code text, name text)
                    JOIN carriers c USING(code)
                    WHERE c.name <> x.name
                )
                """
            )
        ) {
            query.setString(1, carriers);
            query.setString(2, carriers);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                if (row.getBoolean(2)) throw new BusinessException(ErrorCode.CONFLICT);
                if (row.getLong(1) > settings.getScopeCapacity()) throw new BusinessException(
                    ErrorCode.ACCESS_CAPACITY_REACHED,
                    settings.getScopeCapacity()
                );
            }
        }
        int created = 0;
        created += insert(
            connection,
            """
            INSERT INTO category(type, ref_type, code, name, props, sort_order, created_by, updated_by)
            SELECT 'VALUE', type, code, "nameVi", jsonb_build_object('nameEn', "nameEn") ||
                CASE WHEN type='VEHICLE_TYPE' THEN jsonb_build_object('seatCount',
                    CASE code WHEN 'SEAT' THEN 45 WHEN 'SLEEPER' THEN 40 ELSE 9 END) ELSE '{}'::jsonb END,
                "sortOrder", ?, ?
            FROM jsonb_to_recordset(?::jsonb) x(type text, code text, "nameVi" text, "nameEn" text,
                "sortOrder" integer)
            ON CONFLICT (ref_type, code) DO NOTHING
            """,
            data.path("catalogs"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO category(type, ref_type, code, name, props, sort_order, active, created_by, updated_by)
            SELECT 'VALUE', 'PROVINCE', code, "nameVi", jsonb_build_object('nameEn', "nameEn"), "sortOrder", active, ?, ?
            FROM jsonb_to_recordset(?::jsonb) x(code text, "nameVi" text, "nameEn" text, "sortOrder" integer,
                active boolean)
            ON CONFLICT (ref_type, code) DO NOTHING
            """,
            data.path("provinces"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO category(type, ref_type, code, parent_id, name, props, sort_order, active, created_by, updated_by)
            SELECT 'VALUE', 'LOCATION', x.code, w.id, x."nameVi", jsonb_build_object('nameEn', x."nameEn",
                'address', x.address, 'latitude', x.latitude, 'longitude', x.longitude), x."sortOrder", x.active, ?, ?
            FROM jsonb_to_recordset(?::jsonb) x(code text, "provinceCode" text, "wardCode" text, "nameVi" text, "nameEn" text,
                address text, latitude numeric, longitude numeric, "sortOrder" integer, active boolean)
            JOIN category p ON p.type='VALUE' AND p.ref_type='PROVINCE' AND p.code = x."provinceCode"
            JOIN category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.code=x."wardCode" AND w.parent_id=p.id
            ON CONFLICT (ref_type, code) DO NOTHING
            """,
            data.path("locations"),
            actor
        );
        created += insert(
            connection,
            """
            WITH created AS (
                INSERT INTO carriers(scope_id, scope_code, scope_name_vi, scope_name_en, code, name, phone,
                    description, active, created_by, updated_by)
                SELECT nextval('carrier_scope_ids'), 'CARRIER_' || x.code, x.name, x.name, x.code, x.name, x.phone,
                    x.description, x.active, ?, ?
                FROM jsonb_to_recordset(?::jsonb) x(code text, name text, phone text, description text,
                    active boolean)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM carriers c
                    WHERE c.code = x.code
                )
                ON CONFLICT (code) DO NOTHING
                RETURNING scope_id, scope_name_vi, scope_name_en, created_by
            ), history AS (
                INSERT INTO auth_access_changes(event_type, target_kind, target_key, actor_id, before_state,
                    after_state, created_by, updated_by)
                SELECT 'SCOPE_CREATED', 'SCOPE', scope_id::text, created_by, '{}'::jsonb, jsonb_build_object('roles',
                    '[]'::jsonb, 'permissions', '[]'::jsonb, 'resources', '[]'::jsonb, 'mappingCount', 0,
                    'delta', false, 'active', true, 'deleted', false, 'nameVi', scope_name_vi, 'nameEn',
                    scope_name_en, 'version', 0), created_by, created_by
                FROM created
                RETURNING id
            )
            SELECT count(*)
            FROM created
            """,
            data.path("carriers"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO carrier_vehicles(carrier_id, scope_id, code, plate_number, vehicle_type_id, capacity,
                active, created_by, updated_by)
            SELECT c.id, c.scope_id, x.code, x."plateNumber", t.id, x.capacity, x.active, ?, ?
            FROM jsonb_to_recordset(?::jsonb) x(code text, "carrierCode" text, "typeCode" text,
                "plateNumber" text, capacity integer, active boolean)
            JOIN carriers c ON c.code = x."carrierCode"
            JOIN category t ON t.ref_type = 'VEHICLE_TYPE' AND t.type='VALUE'
                AND t.code = x."typeCode"
            ON CONFLICT (carrier_id, code) DO NOTHING
            """,
            data.path("vehicles"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO carrier_drivers(carrier_id, scope_id, code, name, phone, active, created_by,
                updated_by)
            SELECT c.id, c.scope_id, x.code, x.name, x.phone, x.active, ?, ?
            FROM jsonb_to_recordset(?::jsonb) x(code text, "carrierCode" text, name text, phone text, active boolean)
            JOIN carriers c ON c.code = x."carrierCode"
            ON CONFLICT (carrier_id, code) DO NOTHING
            """,
            data.path("drivers"),
            actor
        );
        int catalogs = data.path("catalogs").size(),
            provinces = data.path("provinces").size(),
            locations = data.path("locations").size(),
            companies = data.path("carriers").size(),
            vehicles = data.path("vehicles").size(),
            drivers = data.path("drivers").size();
        return new BusinessDataResponse(
            created,
            catalogs + provinces + locations + companies + vehicles + drivers - created,
            catalogs,
            provinces,
            locations,
            companies,
            vehicles,
            drivers
        );
    }

    /** Mỗi loại dữ liệu đúng một INSERT SELECT, số round-trip không tăng theo số dòng. */
    private static int insert(Connection connection, String sql, JsonNode values, long actor) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, actor);
            query.setLong(2, actor);
            query.setString(3, values.toString());
            if (!query.execute()) return query.getUpdateCount();
            try (ResultSet row = query.getResultSet()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    /** Caller giữ khóa seed/transaction. ON CONFLICT giữ tuyến và điểm dừng đã được người quản trị sửa. */
    public void seedRoutes(Connection connection) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_routes(carrier_id, scope_id, code, name_vi, name_en, description)
                SELECT c.id, c.scope_id, x.code, x.name_vi, x.name_en,
                    'Tuyến nền VietRoute; thời gian và giá chặng do dự án tạo để nghiệm thu, cần xác nhận trước khi công bố chuyến.'
                FROM (
                VALUES ('VRMIENNAM', 'HCMHANOI', 'Thành phố Hồ Chí Minh — Đà Nẵng — Hà Nội',
                    'Ho Chi Minh City — Da Nang — Hanoi'), ('VRBACNAM', 'HANOIHCM',
                    'Hà Nội — Đà Nẵng — Thành phố Hồ Chí Minh', 'Hanoi — Da Nang — Ho Chi Minh City')) x(carrier_code,
                    code, name_vi, name_en)
                JOIN carriers c ON c.code = x.carrier_code
                ON CONFLICT (carrier_id, code) DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_route_stops(route_id, carrier_id, scope_id, location_id, stop_order,
                    arrival_offset_minutes, departure_offset_minutes, cumulative_fare, pickup_allowed,
                    dropoff_allowed)
                SELECT r.id, r.carrier_id, r.scope_id, l.id, x.position, x.arrival, x.departure, x.fare, x.pickup,
                    x.dropoff
                FROM (
                VALUES ('HCMHANOI', 'VRTHUDUC', 1, 0, 0, 0, true, false), ('HCMHANOI', 'VRDANANG', 2, 1080, 1110,
                    450000, true, true), ('HCMHANOI', 'VRHANOI', 3, 2040, 2040, 900000, false, true), ('HANOIHCM',
                    'VRHANOI', 1, 0, 0, 0, true, false), ('HANOIHCM', 'VRDANANG', 2, 960, 990, 450000, true,
                    true), ('HANOIHCM', 'VRTHUDUC', 3, 2040, 2040, 900000, false, true)) x(route_code,
                    location_code, position, arrival, departure, fare, pickup, dropoff)
                JOIN carrier_routes r ON r.code = x.route_code
                JOIN carriers c ON c.id = r.carrier_id
                    AND c.code IN ('VRMIENNAM', 'VRBACNAM')
                JOIN category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.code = x.location_code
                WHERE r.created_by = 0
                ON CONFLICT (route_id, stop_order) DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
    }

    /**
     * Snapshot giả lập chỉ tạo một lần theo mã ổn định, trong transaction/khóa seed của caller.
     * Không sửa chuyến/đơn, khôi phục grant, dịch ngày hoặc trả lại dữ liệu người quản trị đã thay.
     */
    public void seedOperations(Connection connection) throws SQLException {
        JsonNode data = load("bootstrap/operations.json");
        String routes = data.path("routes").toString();
        execute(
            connection,
            """
            WITH input AS (
                SELECT * FROM jsonb_to_recordset(?::jsonb)
                    x("carrierCode" text, code text, "nameVi" text, "nameEn" text, stops jsonb)
            ), added AS (
                INSERT INTO carrier_routes(carrier_id, scope_id, code, name_vi, name_en, description)
                SELECT c.id, c.scope_id, x.code, x."nameVi", x."nameEn",
                    'Lịch trình liên tỉnh do dự án xây dựng; kiểm tra lại trước khi nhận khách thực tế.'
                FROM input x JOIN carriers c ON c.code=x."carrierCode"
                ON CONFLICT (carrier_id,code) DO NOTHING
                RETURNING id,carrier_id,scope_id,code
            )
            INSERT INTO carrier_route_stops(route_id,carrier_id,scope_id,location_id,stop_order,
                arrival_offset_minutes,departure_offset_minutes,cumulative_fare,pickup_allowed,dropoff_allowed)
            SELECT r.id,r.carrier_id,r.scope_id,l.id,s."stopOrder",s.arrival,s.departure,s.fare,s.pickup,s.dropoff
            FROM added r JOIN carriers c ON c.id=r.carrier_id
            JOIN input x ON x.code=r.code AND x."carrierCode"=c.code
            CROSS JOIN LATERAL jsonb_to_recordset(x.stops) s("locationCode" text,"stopOrder" integer,
                arrival integer,departure integer,fare numeric,pickup boolean,dropoff boolean)
            JOIN category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.code=s."locationCode"
            """,
            routes
        );
        // Temp table chứa đúng ID mới INSERT, không đọc lại theo prefix để sửa snapshot đã có.
        execute(connection, "DROP TABLE IF EXISTS pg_temp.vr_init_trips", null);
        execute(
            connection,
            """
            CREATE TEMP TABLE vr_init_trips ON COMMIT DROP AS
            WITH config AS (SELECT ?::jsonb data), input AS (
                SELECT x.*,d.day,coalesce(x."codePrefix",config.data->'profile'->>'version') profile,
                    date_trunc('minute',CURRENT_TIMESTAMP)+INTERVAL '10 minutes' anchor
                FROM config CROSS JOIN LATERAL jsonb_to_recordset(config.data->'routes')
                    x("carrierCode" text,code text,"vehicleCode" text,"driverCode" text,
                        "driverUsername" text,"intervalDays" integer,slot integer,"departureTime" text,
                        "pastDays" integer,"futureDays" integer,"codePrefix" text)
                CROSS JOIN LATERAL generate_series(
                    -coalesce(x."pastDays",(config.data->'profile'->>'pastDays')::integer),
                    coalesce(x."futureDays",(config.data->'profile'->>'futureDays')::integer)) d(day)
                WHERE d.day % x."intervalDays"=0
            ), added AS (
                INSERT INTO carrier_trips(carrier_id,scope_id,route_id,vehicle_id,driver_id,driver_user_id,
                    code,name_vi,name_en,plate_number,vehicle_type_id,vehicle_type_name_vi,
                    vehicle_type_name_en,driver_name,driver_phone,departure_at,end_at,capacity,status,
                    operation_note,created_at,updated_at)
                SELECT c.id,c.scope_id,r.id,v.id,drv.id,u.id,
                    x.profile||'R'||lpad(x.slot::text,2,'0')||'D'||
                        CASE WHEN x."departureTime" IS NOT NULL
                            THEN to_char(t.departure AT TIME ZONE 'Asia/Ho_Chi_Minh','YYYYMMDD')
                            ELSE lpad((x.day+7)::text,2,'0') END,
                    r.name_vi,r.name_en,v.plate_number,v.vehicle_type_id,kind.name,kind.props->>'nameEn',
                    drv.name,drv.phone,t.departure,t.departure+duration.minutes*INTERVAL '1 minute',
                    v.capacity,
                    CASE WHEN x."departureTime" IS NOT NULL THEN CASE
                            WHEN t.departure+duration.minutes*INTERVAL '1 minute'<=CURRENT_TIMESTAMP THEN 'COMPLETED'
                            WHEN t.departure<=CURRENT_TIMESTAMP THEN 'IN_PROGRESS' ELSE 'OPEN' END
                        WHEN x.day<0 THEN CASE WHEN x.day % 5=0 THEN 'CANCELLED' ELSE 'COMPLETED' END
                        WHEN x.day=0 AND x.slot%4=2 THEN 'IN_PROGRESS'
                        WHEN x.day=3 AND x.slot%4=0 THEN 'DRAFT'
                        WHEN x.day=8 AND x.slot%5=0 THEN 'CANCELLED' ELSE 'OPEN' END,
                    CASE WHEN x."departureTime" IS NOT NULL
                            THEN 'Lịch cố định theo giờ Việt Nam; có mặt trước giờ khởi hành 20 phút'
                        WHEN x.day=8 AND x.slot%5=0 THEN 'Hủy lịch chạy do điều chỉnh kế hoạch khai thác'
                        WHEN x.day=0 AND x.slot%4=2 THEN 'Đã khởi hành, đang phục vụ hành khách'
                        ELSE 'Có mặt tại điểm đón trước giờ khởi hành 20 phút' END,
                    least(t.departure-INTERVAL '3 days',CURRENT_TIMESTAMP-INTERVAL '1 day'),
                    least(t.departure-INTERVAL '2 days',CURRENT_TIMESTAMP-INTERVAL '12 hours')
                FROM input x JOIN carriers c ON c.code=x."carrierCode" AND c.active AND c.scope_active
                JOIN carrier_routes r ON r.carrier_id=c.id AND r.code=x.code AND r.active
                JOIN carrier_vehicles v ON v.carrier_id=c.id AND v.code=x."vehicleCode" AND v.active
                JOIN carrier_drivers drv ON drv.carrier_id=c.id AND drv.code=x."driverCode" AND drv.active
                JOIN auth_users u ON u.id=drv.user_id AND u.username=x."driverUsername" AND u.active
                    AND NOT u.deleted AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
                JOIN category kind ON kind.id=v.vehicle_type_id AND kind.ref_type='VEHICLE_TYPE' AND kind.type='VALUE' AND kind.active
                CROSS JOIN LATERAL (SELECT max(departure_offset_minutes) minutes,
                    count(*) count FROM carrier_route_stops WHERE route_id=r.id) duration
                CROSS JOIN LATERAL (SELECT CASE WHEN x."departureTime" IS NOT NULL THEN
                        ((c.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+x.day+x."departureTime"::time)
                            AT TIME ZONE 'Asia/Ho_Chi_Minh'
                    ELSE x.anchor+x.day*INTERVAL '1 day'
                        +CASE WHEN x.day=0 AND x.slot=1 THEN INTERVAL '0 minutes'
                            WHEN x.day=0 AND x.slot%4=2 THEN INTERVAL '-90 minutes'
                            ELSE (x.slot%4)*INTERVAL '45 minutes' END END departure) t
                WHERE duration.count>=2
                    AND EXISTS (SELECT 1 FROM auth_user_roles ur JOIN auth_roles role ON role.code=ur.role_code
                        WHERE ur.user_id=u.id AND ur.scope_id=c.scope_id AND ur.active
                            AND role.active AND NOT role.deleted)
                ON CONFLICT (carrier_id,code) DO NOTHING
                RETURNING id,carrier_id,scope_id,route_id,code,status,departure_at,end_at,capacity
            ) SELECT * FROM added
            """,
            data.toString()
        );
        execute(
            connection,
            """
            INSERT INTO trip_stops(trip_id,carrier_id,scope_id,location_id,province_id,stop_order,
                name_vi,name_en,address,latitude,longitude,scheduled_arrival_at,scheduled_departure_at,
                actual_arrival_at,actual_departure_at,cumulative_fare,pickup_allowed,dropoff_allowed)
            SELECT t.id,t.carrier_id,t.scope_id,l.id,w.parent_id,s.stop_order,l.name AS name_vi,l.props->>'nameEn' AS name_en,
                l.props->>'address' AS address,(l.props->>'latitude')::numeric AS latitude,(l.props->>'longitude')::numeric AS longitude,t.departure_at+s.arrival_offset_minutes*INTERVAL '1 minute',
                t.departure_at+s.departure_offset_minutes*INTERVAL '1 minute',
                CASE WHEN t.status='COMPLETED' OR t.status='IN_PROGRESS' AND s.stop_order=1
                    THEN t.departure_at+s.arrival_offset_minutes*INTERVAL '1 minute' END,
                CASE WHEN t.status='COMPLETED' AND s.pickup_allowed
                        OR t.status='IN_PROGRESS' AND s.stop_order=1
                    THEN t.departure_at+s.departure_offset_minutes*INTERVAL '1 minute' END,
                s.cumulative_fare,s.pickup_allowed,s.dropoff_allowed
            FROM vr_init_trips t JOIN carrier_route_stops s ON s.route_id=t.route_id
            JOIN category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.id=s.location_id
            JOIN category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.id=l.parent_id
            """,
            null
        );
        execute(
            connection,
            """
            INSERT INTO trip_segments(trip_id,carrier_id,scope_id,segment_order,capacity)
            SELECT t.id,t.carrier_id,t.scope_id,s.stop_order,t.capacity
            FROM vr_init_trips t JOIN trip_stops s ON s.trip_id=t.id
            WHERE s.stop_order<(SELECT max(stop_order) FROM trip_stops WHERE trip_id=t.id)
            """,
            null
        );
        seedTripEvents(connection);
        seedBookings(connection, data);
    }

    /** Mốc giả lập đầy đủ theo trạng thái; audit actor system, không giả hành động đăng nhập thực. */
    private void seedTripEvents(Connection connection) throws SQLException {
        execute(
            connection,
            """
            INSERT INTO trip_events(trip_id,carrier_id,scope_id,stop_order,event_type,note,created_at)
            SELECT t.id,t.carrier_id,t.scope_id,e.stop_order,e.event_type,e.note,e.event_at
            FROM vr_init_trips t CROSS JOIN LATERAL (
                SELECT NULL::integer stop_order,'CREATED'::text event_type,'Lập kế hoạch chạy tuyến'::text note,
                    least(t.departure_at-INTERVAL '3 days',CURRENT_TIMESTAMP-INTERVAL '1 day') AS event_at
                UNION ALL SELECT NULL,'OPENED','Công bố lịch và tiếp nhận đặt chỗ',
                    least(t.departure_at-INTERVAL '2 days',CURRENT_TIMESTAMP-INTERVAL '12 hours') WHERE t.status<>'DRAFT'
                UNION ALL SELECT 1,'STARTED','Xác nhận khởi hành tại điểm đầu',t.departure_at
                    WHERE t.status IN ('IN_PROGRESS','COMPLETED')
                UNION ALL SELECT s.stop_order,'ARRIVED','Đến điểm đón trả theo hành trình',s.actual_arrival_at
                    FROM trip_stops s WHERE s.trip_id=t.id AND s.stop_order>1 AND s.actual_arrival_at IS NOT NULL
                UNION ALL SELECT s.stop_order,'DEPARTED','Rời điểm, tiếp tục hành trình',s.actual_departure_at
                    FROM trip_stops s WHERE s.trip_id=t.id AND s.stop_order>1 AND s.actual_departure_at IS NOT NULL
                UNION ALL SELECT NULL,'COMPLETED','Hoàn tất phục vụ hành khách',t.end_at WHERE t.status='COMPLETED'
                UNION ALL SELECT NULL,'CANCELLED','Điều chỉnh kế hoạch khai thác',
                    least(t.departure_at-INTERVAL '12 hours',CURRENT_TIMESTAMP-INTERVAL '3 hours') WHERE t.status='CANCELLED'
            ) e
            """,
            null
        );
    }

    /** Mỗi nhóm INSERT theo tập, số query cố định; giá/chỗ/tiền/history được suy từ cùng snapshot. */
    private void seedBookings(Connection connection, JsonNode data) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS pg_temp.vr_init_bookings", null);
        execute(
            connection,
            """
            CREATE TEMP TABLE vr_init_bookings ON COMMIT DROP AS
            WITH config AS (SELECT ?::jsonb data), candidates AS (
                SELECT t.*,i.number,NULL::bigint customer,'PHONE'::text source,
                    'P'||i.number kind,
                    CASE WHEN t.status='CANCELLED' THEN 'CANCELLED'
                        WHEN i.number=5 THEN 'CANCELLED' WHEN i.number=6 THEN 'REJECTED'
                        WHEN t.status='COMPLETED' THEN 'COMPLETED'
                        WHEN i.number<=2 AND t.status<>'IN_PROGRESS' THEN 'PENDING'
                        ELSE 'CONFIRMED' END booking_status
                FROM vr_init_trips t CROSS JOIN config
                CROSS JOIN LATERAL generate_series(1,(config.data->'profile'->>'phoneBookingsPerTrip')::integer) i(number)
                WHERE t.status<>'DRAFT'
                UNION ALL
                SELECT t.*,u.ordinality::integer,u.id,'WEB',u.kind,
                    CASE WHEN t.status='COMPLETED' THEN 'COMPLETED'
                        WHEN u.ordinality%2=1 THEN 'PENDING' ELSE 'CONFIRMED' END
                FROM config CROSS JOIN LATERAL (
                    SELECT a.id,x.ordinality,k.kind FROM jsonb_array_elements_text(
                        config.data->'customerUsernames') WITH ORDINALITY x(username,ordinality)
                    JOIN auth_users a ON a.username=x.username AND a.active AND NOT a.deleted
                    CROSS JOIN (VALUES ('WA'),('WH')) k(kind)
                ) u
                JOIN LATERAL (SELECT v.* FROM vr_init_trips v WHERE
                    CASE WHEN u.kind='WH' THEN v.status='COMPLETED' ELSE
                        v.status='OPEN' AND v.departure_at>CURRENT_TIMESTAMP+INTERVAL '12 hours' END
                    ORDER BY v.departure_at,v.code LIMIT 1 OFFSET ((u.ordinality-1)%12)) t ON true
            ), priced AS (
                SELECT x.*,first.id pickup_id,first.stop_order pickup_order,last.id dropoff_id,
                    last.stop_order dropoff_order,last.cumulative_fare-first.cumulative_fare unit_price,
                    1+(x.number%2) seats,contact.name contact_name,contact.phone,
                    CASE WHEN x.booking_status IN ('CONFIRMED','COMPLETED','CANCELLED')
                        THEN CASE WHEN x.number%2=0 THEN 0.5 ELSE 1 END ELSE 0 END pay_ratio
                FROM candidates x CROSS JOIN config
                JOIN LATERAL (SELECT s.* FROM trip_stops s WHERE s.trip_id=x.id AND s.pickup_allowed
                    ORDER BY stop_order LIMIT 1) first ON true
                JOIN LATERAL (SELECT s.* FROM trip_stops s WHERE s.trip_id=x.id AND s.dropoff_allowed
                    ORDER BY stop_order DESC LIMIT 1) last ON true
                CROSS JOIN LATERAL jsonb_to_record(config.data->'contacts'->((x.number-1)%12)) contact(name text,phone text)
            ), added AS (
                INSERT INTO bookings(trip_id,carrier_id,scope_id,customer_id,source_code,source_id,
                    pickup_stop_id,dropoff_stop_id,pickup_order,dropoff_order,seats,unit_price,total_price,
                    contact_name,contact_phone,status,request_key,paid_amount,refunded_amount,created_at,updated_at)
                SELECT p.id,p.carrier_id,p.scope_id,p.customer,p.source,source.id,p.pickup_id,p.dropoff_id,
                    p.pickup_order,p.dropoff_order,p.seats,p.unit_price,p.unit_price*p.seats,p.contact_name,p.phone,
                    p.booking_status,md5(p.code||':'||p.kind||':'||p.number)::uuid,
                    p.unit_price*p.seats*p.pay_ratio,
                    CASE WHEN p.booking_status='CANCELLED' AND p.number%2=1
                        THEN p.unit_price*p.seats*p.pay_ratio ELSE 0 END,
                    least(p.departure_at-INTERVAL '36 hours',CURRENT_TIMESTAMP-INTERVAL '6 hours'),
                    CASE WHEN p.booking_status='COMPLETED' THEN p.end_at
                        ELSE least(p.departure_at-INTERVAL '30 hours',CURRENT_TIMESTAMP-INTERVAL '5 hours') END
                FROM priced p JOIN category source ON source.ref_type='BOOKING_SOURCE' AND source.type='VALUE' AND source.code=p.source
                WHERE p.source='PHONE' OR p.booking_status='COMPLETED' OR NOT EXISTS (
                    SELECT 1 FROM bookings b WHERE b.customer_id=p.customer AND b.source_code='WEB'
                        AND b.status IN ('PENDING','CONFIRMED'))
                ON CONFLICT (request_key) DO NOTHING
                RETURNING *
            ) SELECT * FROM added
            """,
            data.toString()
        );
        execute(
            connection,
            """
            UPDATE trip_segments segment SET reserved_seats=occupied.seats
            FROM (SELECT s.trip_id,s.segment_order,coalesce(sum(b.seats),0)::integer seats
                FROM trip_segments s JOIN vr_init_trips t ON t.id=s.trip_id
                LEFT JOIN vr_init_bookings b ON b.trip_id=s.trip_id AND b.status IN ('CONFIRMED','COMPLETED')
                    AND b.pickup_order<=s.segment_order AND b.dropoff_order>s.segment_order
                GROUP BY s.trip_id,s.segment_order) occupied
            WHERE segment.trip_id=occupied.trip_id AND segment.segment_order=occupied.segment_order
            """,
            null
        );
        execute(
            connection,
            """
            INSERT INTO booking_events(booking_id,trip_id,carrier_id,scope_id,from_status,to_status,
                event_type,amount,note,request_key,created_at)
            SELECT b.id,b.trip_id,b.carrier_id,b.scope_id,e.from_state,e.to_state,e.kind,e.amount,e.note,
                md5(b.request_key::text||':'||e.kind)::uuid,e.event_at
            FROM vr_init_bookings b CROSS JOIN LATERAL (
                SELECT NULL::text from_state,'PENDING'::text to_state,'CREATED'::text kind,
                    0::numeric amount,'Tiếp nhận yêu cầu đặt chỗ'::text note,b.created_at AS event_at
                UNION ALL SELECT 'PENDING','CONFIRMED','CONFIRMED',0,'Xác nhận chỗ theo chặng',
                    b.created_at+INTERVAL '10 minutes'
                    WHERE b.status IN ('CONFIRMED','COMPLETED') OR b.status='CANCELLED' AND b.paid_amount>0
                UNION ALL SELECT 'CONFIRMED','CONFIRMED','PAYMENT',b.paid_amount,'Ghi nhận tiền thực thu',
                    b.created_at+INTERVAL '20 minutes' WHERE b.paid_amount>0
                UNION ALL SELECT CASE WHEN b.paid_amount>0 THEN 'CONFIRMED' ELSE 'PENDING' END,
                    b.status,b.status,0,CASE WHEN b.status='CANCELLED' THEN 'Khách đổi kế hoạch di chuyển'
                        ELSE 'Không tiếp nhận yêu cầu tại thời điểm xử lý' END,b.created_at+INTERVAL '30 minutes'
                    WHERE b.status IN ('CANCELLED','REJECTED')
                UNION ALL SELECT 'CANCELLED','CANCELLED','REFUND',b.refunded_amount,'Hoàn tiền theo yêu cầu hủy',
                    b.created_at+INTERVAL '40 minutes' WHERE b.refunded_amount>0
                UNION ALL SELECT 'CONFIRMED','COMPLETED','COMPLETED',0,'Đã trả khách tại điểm đến',
                    b.updated_at WHERE b.status='COMPLETED'
            ) e
            """,
            null
        );
    }

    /** Statement có tối đa một tham số JSON; transaction/commit luôn thuộc caller. */
    private void execute(Connection connection, String sql, String values) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            if (values != null) query.setString(1, values);
            query.execute();
        }
    }
}
