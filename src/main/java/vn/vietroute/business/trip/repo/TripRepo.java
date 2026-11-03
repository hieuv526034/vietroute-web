package vn.vietroute.business.trip.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.decimalValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.trip.constant.TripChoiceKind;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripEventResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.business.trip.dto.search.TripChoiceSearch;
import vn.vietroute.business.trip.dto.search.TripSearch;
import vn.vietroute.business.trip.repo.custom.TripSegmentCustomRepo;
import vn.vietroute.business.trip.repo.custom.TripStopCustomRepo;
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Native SQL cho chuyến, scope và lịch sử; ghi lô snapshot chuyển sang CustomRepo. */
@Repository
public class TripRepo extends BaseRepository {

    private static final String FROM = """
    FROM {h-schema}carrier_trips t
    JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
    """;
    private static final String COLUMNS = """
    t.id, t.carrier_id, t.scope_id, owner.name AS carrier_name, owner.phone AS carrier_phone, t.route_id,
        t.code, t.name_vi, t.name_en, t.vehicle_id, t.plate_number, t.vehicle_type_id, t.vehicle_type_name_vi,
        t.vehicle_type_name_en, t.driver_id, t.driver_user_id, t.driver_name, t.driver_phone, t.departure_at,
        t.end_at, t.capacity, t.status, t.operation_note, t.version, (%s) AS can_update
    """.formatted(UserRoleRepo.visibleSql("updateTarget"));
    private static final String ASSIGNED = """
        AND t.status <> 'DRAFT'
        AND t.driver_user_id = :driverUserId
        AND EXISTS (
            SELECT 1
            FROM {h-schema}carrier_drivers cd
            WHERE cd.id = t.driver_id AND cd.carrier_id = t.carrier_id AND cd.scope_id = t.scope_id
                AND cd.active
                AND cd.user_id = t.driver_user_id
        )
        AND EXISTS (
            SELECT 1
            FROM (
                SELECT scope_id, user_id, BOOL_AND(active) AS active, MAX(version) AS version
                FROM {h-schema}auth_user_roles
                WHERE scope_id > 0
                GROUP BY scope_id, user_id
            ) dm
            JOIN (
                SELECT scope_id, user_id, role_code
                FROM {h-schema}auth_user_roles
                WHERE scope_id > 0 AND active
            ) dr ON dr.scope_id = dm.scope_id
                AND dr.user_id = dm.user_id
            JOIN {h-schema}auth_user_roles du ON du.user_id = dr.user_id AND du.role_code = dr.role_code
            JOIN {h-schema}auth_roles role ON role.code = du.role_code AND role.active AND NOT role.deleted
            WHERE dm.scope_id = t.scope_id AND dm.user_id = t.driver_user_id AND dm.active
        )
    """;

    private final TripSegmentCustomRepo segments;
    private final TripStopCustomRepo stops;

    public TripRepo(EntityManager manager, TripSegmentCustomRepo segments, TripStopCustomRepo stops) {
        super(manager);
        this.segments = segments;
        this.stops = stops;
    }

    /** Count và trang cùng bộ lọc ngày xuất phát Việt Nam, không lọc lại trong RAM. */
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> page(TripSearch search) {
        return page(search, null);
    }

    /** Chỉ chuyến được giao và hồ sơ DRIVER/membership hiện hành, kể cả người gọi cũng có ADMIN. */
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> assignedPage(TripSearch search, long driverUserId) {
        return page(search, driverUserId);
    }

    private PageResponse<TripResponse> page(TripSearch search, Long driverUserId) {
        String filter = """
        %s
        WHERE %s
            AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            AND (CAST(:status AS TEXT) IS NULL OR t.status = :status)
            AND (CAST(:fromAt AS TIMESTAMPTZ) IS NULL OR (t.departure_at >= :fromAt AND t.departure_at < :toAt))
            AND STRPOS(LOWER(CONCAT_WS(' ', t.code, t.name_vi, t.name_en, t.plate_number, owner.name)),
            :keyword) > 0
        %s
        """.formatted(FROM, UserRoleRepo.visibleSql(), driverUserId == null ? "" : " " + ASSIGNED);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            bindSearch(query, search, driverUserId);

            Tuple row = (Tuple) query.getSingleResult();
            total = longValue(row.get(0));
        }
        ArrayList<TripResponse> result = new ArrayList<TripResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                ORDER BY t.departure_at DESC, t.id DESC
                LIMIT :pageSize OFFSET :offset
                """.formatted(COLUMNS, filter),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query::setParameter, "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID);
            bindSearch(query, search, driverUserId);
            query.setParameter("pageSize", search.getSize());
            query.setParameter("offset", search.offset());

            for (Object rowValue : query.getResultList()) {
                Tuple row = (Tuple) rowValue;
                result.add(map(row, List.of(), List.of()));
            }
        }
        return PageResponse.of(result, search, total);
    }

    private void bindSearch(Query query, TripSearch body, Long driverUserId) {
        UserRoleRepo.bindVisible(query::setParameter);
        query.setParameter("carrierId", body.getCarrierId());
        query.setParameter("status", body.getStatus() == null ? null : body.getStatus().name());
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        Instant from = body.getDate() == null ? null : body.getDate().atStartOfDay(zone).toInstant();
        Instant to = body.getDate() == null ? null : body.getDate().plusDays(1).atStartOfDay(zone).toInstant();
        query.setParameter("fromAt", from);
        query.setParameter("toAt", to);
        query.setParameter("keyword", body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT));
        if (driverUserId != null) query.setParameter("driverUserId", driverUserId);
    }

    /** ID ngoài scope trả 404; UPDATE khóa trip trước khi service khóa tài nguyên phân công. */
    public TripResponse find(long id, boolean lock, boolean detail) {
        return find(id, lock, detail, null);
    }

    /** Tài xế ngoài phân công hiện hành nhận 404 trước đọc điểm/lịch sử hoặc khóa mutation. */
    public TripResponse assigned(long id, boolean lock, long driverUserId) {
        return find(id, lock, true, driverUserId);
    }

    private TripResponse find(long id, boolean lock, boolean detail, Long driverUserId) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                WHERE t.id = :tripId
                    AND %s
                %s
                %s
                """.formatted(
                    COLUMNS,
                    FROM,
                    UserRoleRepo.visibleSql(),
                    driverUserId == null ? "" : ASSIGNED,
                    lock ? " FOR UPDATE OF t" : ""
                ),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query::setParameter, "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID);
            query.setParameter("tripId", id);
            UserRoleRepo.bindVisible(query::setParameter);
            if (driverUserId != null) query.setParameter("driverUserId", driverUserId);

            List<?> rowValues = query.getResultList();

            if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
            Tuple row = (Tuple) rowValues.get(0);

            return map(row, detail ? stops(id) : List.of(), detail ? events(id) : List.of());
        }
    }

    /** Chỉ caller đã xác minh trip/scope mới đọc snapshot điểm; thời gian đổi Timestamp tại ranh JDBC. */
    public List<TripStopResponse> stops(long tripId) {
        ArrayList<TripStopResponse> result = new ArrayList<TripStopResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT id, location_id, province_id, stop_order, name_vi, name_en, address, latitude, longitude,
                    scheduled_arrival_at, scheduled_departure_at, actual_arrival_at, actual_departure_at,
                    cumulative_fare, pickup_allowed, dropoff_allowed
                FROM {h-schema}trip_stops
                WHERE trip_id = :tripId
                ORDER BY stop_order
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);

            for (Object rowValue : query.getResultList()) {
                Tuple row = (Tuple) rowValue;
                result.add(
                    new TripStopResponse(
                        longValue(row.get(0)),
                        longValue(row.get(1)),
                        longValue(row.get(2)),
                        intValue(row.get(3)),
                        stringValue(row.get(4)),
                        stringValue(row.get(5)),
                        stringValue(row.get(6)),
                        decimalValue(row.get(7)),
                        decimalValue(row.get(8)),
                        instant(row, 10),
                        instant(row, 11),
                        instant(row, 12),
                        instant(row, 13),
                        decimalValue(row.get(13)),
                        booleanValue(row.get(14)),
                        booleanValue(row.get(15))
                    )
                );
            }
        }
        return List.copyOf(result);
    }

    /** Lịch sử mới nhất giới hạn 100, xếp thời gian thuận cho UI, không join email/account credentials. */
    private List<TripEventResponse> events(long tripId) {
        ArrayList<TripEventResponse> result = new ArrayList<TripEventResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT id, event_type, stop_order, note, created_at
                FROM (
                    SELECT id, event_type, stop_order, note, created_at
                    FROM {h-schema}trip_events
                    WHERE trip_id = :tripId
                    ORDER BY id DESC
                    LIMIT 100
                ) e
                ORDER BY id
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);

            for (Object rowValue : query.getResultList()) {
                Tuple row = (Tuple) rowValue;
                result.add(
                    new TripEventResponse(
                        longValue(row.get(0)),
                        stringValue(row.get(1)),
                        row.get(2, Integer.class),
                        stringValue(row.get(3)),
                        instant(row, 5)
                    )
                );
            }
        }
        return List.copyOf(result);
    }

    /** Tạo trip, stop snapshot, từng segment và CREATED trong một transaction, không tự publish. */
    public long insert(
        Connection connection,
        TripRequest body,
        Template template,
        Assets assets,
        Instant end,
        long actor
    ) throws SQLException {
        long id;
        {
            Query query = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}carrier_trips(carrier_id, scope_id, route_id, vehicle_id, driver_id, driver_user_id,
                    code, name_vi, name_en, plate_number, vehicle_type_id, vehicle_type_name_vi,
                    vehicle_type_name_en, driver_name, driver_phone, departure_at, end_at, capacity, created_by,
                    updated_by)
                VALUES (:carrierId, :scopeId, :templateId, :vehicleId, :driverId, :driverUserId, :code, :nameVi, :nameEn, :plate, :typeId, :typeVi, :typeEn, :driverName, :driverPhone, :departureAt, :endAt, :capacity, :actor, :actor)
                RETURNING id
                """,
                Tuple.class
            );

            query.setParameter("carrierId", template.getCarrierId());
            query.setParameter("scopeId", template.getScopeId());
            query.setParameter("templateId", template.getId());
            query.setParameter("vehicleId", assets.getVehicleId());
            query.setParameter("driverId", assets.getDriverId());
            query.setParameter("driverUserId", assets.getDriverUserId());
            query.setParameter("code", body.getCode());
            query.setParameter("nameVi", template.getNameVi());
            query.setParameter("nameEn", template.getNameEn());
            query.setParameter("plate", assets.getPlate());
            query.setParameter("typeId", assets.getTypeId());
            query.setParameter("typeVi", assets.getTypeVi());
            query.setParameter("typeEn", assets.getTypeEn());
            query.setParameter("driverName", assets.getDriverName());
            query.setParameter("driverPhone", assets.getDriverPhone());
            query.setParameter("departureAt", Timestamp.from(body.getDepartureAt()));
            query.setParameter("endAt", Timestamp.from(end));
            query.setParameter("capacity", body.getCapacity());
            query.setParameter("actor", actor);

            Tuple row = (Tuple) query.getSingleResult();
            id = longValue(row.get(0));
        }
        stops.insertBatch(connection, id, template, body.getDepartureAt(), actor);
        segments.insertBatch(
            connection,
            id,
            template.getCarrierId(),
            template.getScopeId(),
            template.getStops().size(),
            body.getCapacity(),
            actor
        );
        event(id, template.getCarrierId(), template.getScopeId(), "CREATED", null, "", actor);
        return id;
    }

    /** Kiểm active của các địa điểm snapshot gốc, kể cả mẫu tuyến đã thay sang địa điểm khác. */
    public void requireStopsActive(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT l.active
                    AND w.active AND p.active
                FROM {h-schema}trip_stops s
                JOIN {h-schema}category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.id = s.location_id
                JOIN {h-schema}category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.id=l.parent_id
                JOIN {h-schema}category p ON p.type='VALUE' AND p.ref_type='PROVINCE' AND p.id = s.province_id
                WHERE s.trip_id = :id
                ORDER BY s.stop_order
                FOR SHARE OF p, w, l
                """,
                Tuple.class
            );

            query.setParameter("id", id);

            for (Object rowsValue : query.getResultList()) {
                Tuple rows = (Tuple) rowsValue;
                if (!booleanValue(rows.get(0))) throw new BusinessException(ErrorCode.TRIP_STATE_CONFLICT);
            }
        }
    }

    /** CAS phân công và dịch giờ DRAFT; các mốc actual không được thay bằng thao tác staff. */
    public void assignment(
        Connection connection,
        TripResponse trip,
        Assets assets,
        int capacity,
        Instant departure,
        Instant end,
        long actor
    ) throws SQLException {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}carrier_trips
                SET vehicle_id = :vehicleId, driver_id = :driverId, driver_user_id = :driverUserId, plate_number = :plate, vehicle_type_id = :typeId,
                    vehicle_type_name_vi = :typeVi, vehicle_type_name_en = :typeEn, driver_name = :driverName, driver_phone = :driverPhone,
                    capacity = :capacity, departure_at = :departureAt, end_at = :endAt, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :tripVersion
                """
            );

            query.setParameter("vehicleId", assets.getVehicleId());
            query.setParameter("driverId", assets.getDriverId());
            query.setParameter("driverUserId", assets.getDriverUserId());
            query.setParameter("plate", assets.getPlate());
            query.setParameter("typeId", assets.getTypeId());
            query.setParameter("typeVi", assets.getTypeVi());
            query.setParameter("typeEn", assets.getTypeEn());
            query.setParameter("driverName", assets.getDriverName());
            query.setParameter("driverPhone", assets.getDriverPhone());
            query.setParameter("capacity", capacity);
            query.setParameter("departureAt", Timestamp.from(departure));
            query.setParameter("endAt", Timestamp.from(end));
            query.setParameter("actor", actor);
            query.setParameter("tripId", trip.getId());
            query.setParameter("carrierId", trip.getCarrierId());
            query.setParameter("scopeId", trip.getScopeId());
            query.setParameter("tripVersion", trip.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        long delta = departure.getEpochSecond() - trip.getDepartureAt().getEpochSecond();
        stops.shiftSchedules(connection, trip.getId(), delta, actor);
        segments.updateCapacity(connection, trip.getId(), capacity, actor);
    }

    /** Mọi state transition đều tăng version; caller kiểm đồ thị trạng thái trước khi ghi. */
    public void state(TripResponse trip, TripStatus status, String note, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}carrier_trips
                SET status = :status, operation_note = :note, updated_by = :actor,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :tripVersion
                """
            );

            query.setParameter("status", status.name());
            query.setParameter("note", note);
            query.setParameter("actor", actor);
            query.setParameter("tripId", trip.getId());
            query.setParameter("carrierId", trip.getCarrierId());
            query.setParameter("scopeId", trip.getScopeId());
            query.setParameter("tripVersion", trip.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Nếu ghi lịch sử lỗi, exception đi ra ngoài để rollback cả mutation. */
    public void event(long tripId, long carrierId, long scopeId, String type, Integer order, String note, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}trip_events(trip_id, carrier_id, scope_id, event_type, stop_order, note, created_by)
                VALUES (:tripId, :carrierId, :scopeId, :type, :order, :note, :actor)
                """
            );

            query.setParameter("tripId", tripId);
            query.setParameter("carrierId", carrierId);
            query.setParameter("scopeId", scopeId);
            query.setParameter("type", type);
            query.setParameter("order", order);
            query.setParameter("note", note);
            query.setParameter("actor", actor);
            query.executeUpdate();
        }
    }

    private static Instant instant(Tuple row, int at) {
        return instantValue(row.get(at - 1));
    }

    private static TripResponse map(Tuple row, List<TripStopResponse> stops, List<TripEventResponse> events) {
        return new TripResponse(
            longValue(row.get("id")),
            longValue(row.get("carrier_id")),
            longValue(row.get("scope_id")),
            stringValue(row.get("carrier_name")),
            stringValue(row.get("carrier_phone")),
            longValue(row.get("route_id")),
            stringValue(row.get("code")),
            stringValue(row.get("name_vi")),
            stringValue(row.get("name_en")),
            longValue(row.get("vehicle_id")),
            stringValue(row.get("plate_number")),
            longValue(row.get("vehicle_type_id")),
            stringValue(row.get("vehicle_type_name_vi")),
            stringValue(row.get("vehicle_type_name_en")),
            longValue(row.get("driver_id")),
            longValue(row.get("driver_user_id")),
            stringValue(row.get("driver_name")),
            stringValue(row.get("driver_phone")),
            instantValue(row.get("departure_at")),
            instantValue(row.get("end_at")),
            intValue(row.get("capacity")),
            TripStatus.valueOf(stringValue(row.get("status"))),
            stringValue(row.get("operation_note")),
            longValue(row.get("version")),
            booleanValue(row.get("can_update")),
            stops,
            events
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Template {

        private long id;

        private long carrierId;

        private long scopeId;

        private String nameVi;

        private String nameEn;

        private boolean active;

        private List<SourceStop> stops;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class SourceStop {

        private long locationId;

        private long provinceId;

        private int order;

        private String nameVi;

        private String nameEn;

        private String address;

        private BigDecimal latitude;

        private BigDecimal longitude;

        private int arrival;

        private int departure;

        private BigDecimal fare;

        private boolean pickup;

        private boolean dropoff;

        private boolean active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Assets {

        private long vehicleId;

        private long driverId;

        private long driverUserId;

        private String plate;

        private long typeId;

        private String typeVi;

        private String typeEn;

        private int capacity;

        private String driverName;

        private String driverPhone;
    }

    /** Mẫu chỉ đọc sau khi service xác định nhà xe bằng guard; khóa tuyến ngăn thay điểm trong lúc chụp. */
    public Template template(long routeId, Long carrierId, boolean lock) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT id, carrier_id, scope_id, name_vi, name_en, active
                FROM {h-schema}carrier_routes
                WHERE id = :routeId
                    AND (CAST(:carrierId AS BIGINT) IS NULL OR carrier_id = :carrierId)
                %s
                """.formatted(lock ? " FOR SHARE" : ""),
                Tuple.class
            );

            query.setParameter("routeId", routeId);
            query.setParameter("carrierId", carrierId);

            List<?> rowValues = query.getResultList();

            if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
            Tuple row = (Tuple) rowValues.get(0);

            return new Template(
                longValue(row.get(0)),
                longValue(row.get(1)),
                longValue(row.get(2)),
                stringValue(row.get(3)),
                stringValue(row.get(4)),
                booleanValue(row.get(5)),
                lock ? sourceStops(routeId) : List.of()
            );
        }
    }

    /** Khóa chia sẻ địa điểm/tỉnh để snapshot và kiểm active thuộc cùng transaction. */
    private List<SourceStop> sourceStops(long routeId) {
        ArrayList<SourceStop> result = new ArrayList<SourceStop>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT l.id, p.id AS province_id, s.stop_order, l.name AS name_vi, l.props->>'nameEn' AS name_en, l.props->>'address' AS address, (l.props->>'latitude')::NUMERIC AS latitude, (l.props->>'longitude')::NUMERIC AS longitude,
                    s.arrival_offset_minutes, s.departure_offset_minutes, s.cumulative_fare,
                    s.pickup_allowed, s.dropoff_allowed,
                    l.active
                    AND w.active AND p.active AS usable
                FROM {h-schema}carrier_route_stops s
                JOIN {h-schema}category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.id = s.location_id
                JOIN {h-schema}category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.id=l.parent_id
                JOIN {h-schema}category p ON p.type='VALUE' AND p.ref_type='PROVINCE' AND p.id=w.parent_id
                WHERE s.route_id = :routeId
                ORDER BY s.stop_order
                FOR SHARE OF p, w, l
                """,
                Tuple.class
            );

            query.setParameter("routeId", routeId);

            for (Object rowValue : query.getResultList()) {
                Tuple row = (Tuple) rowValue;
                result.add(
                    new SourceStop(
                        longValue(row.get(0)),
                        longValue(row.get(1)),
                        intValue(row.get(2)),
                        stringValue(row.get(3)),
                        stringValue(row.get(4)),
                        stringValue(row.get(5)),
                        decimalValue(row.get(6)),
                        decimalValue(row.get(7)),
                        intValue(row.get(8)),
                        intValue(row.get(9)),
                        decimalValue(row.get(10)),
                        booleanValue(row.get(11)),
                        booleanValue(row.get(12)),
                        booleanValue(row.get(13))
                    )
                );
            }
        }
        BusinessRules.require(result.size() >= 2 && result.size() <= 32, ErrorCode.TRIP_INVALID);
        return List.copyOf(result);
    }

    /** Khóa xe rồi hồ sơ tài xế, thứ tự thống nhất để các chuyến cạnh tranh cùng tài nguyên được tuần tự hóa. */
    public Assets assets(long carrierId, long scopeId, long vehicleId, long driverId) {
        String plate;
        long typeId;
        String typeVi;
        String typeEn;
        int capacity;
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT v.plate_number, v.vehicle_type_id, t.name AS name_vi, t.props->>'nameEn' AS name_en, v.capacity
                FROM {h-schema}carrier_vehicles v
                JOIN {h-schema}category t ON t.id = v.vehicle_type_id
                    AND t.ref_type = 'VEHICLE_TYPE' AND t.type='VALUE'
                    AND t.active
                WHERE v.id = :vehicleId AND v.carrier_id = :carrierId AND v.scope_id = :scopeId AND v.active
                FOR UPDATE OF v
                FOR SHARE OF t
                """,
                Tuple.class
            );

            query.setParameter("vehicleId", vehicleId);
            query.setParameter("carrierId", carrierId);
            query.setParameter("scopeId", scopeId);

            List<?> rowValues = query.getResultList();

            if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.TRIP_INVALID);
            Tuple row = (Tuple) rowValues.get(0);

            plate = stringValue(row.get(0));
            typeId = longValue(row.get(1));
            typeVi = stringValue(row.get(2));
            typeEn = stringValue(row.get(3));
            capacity = intValue(row.get(4));
        }
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT user_id, name, phone
                FROM {h-schema}carrier_drivers
                WHERE id = :driverId AND carrier_id = :carrierId AND scope_id = :scopeId AND active AND user_id IS NOT NULL
                FOR UPDATE
                """,
                Tuple.class
            );

            query.setParameter("driverId", driverId);
            query.setParameter("carrierId", carrierId);
            query.setParameter("scopeId", scopeId);

            List<?> rowValues = query.getResultList();

            if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.TRIP_INVALID);
            Tuple row = (Tuple) rowValues.get(0);

            return new Assets(
                vehicleId,
                driverId,
                longValue(row.get(0)),
                plate,
                typeId,
                typeVi,
                typeEn,
                capacity,
                stringValue(row.get(1)),
                stringValue(row.get(2))
            );
        }
    }

    /** Gọi sau khóa assets; không khóa chuyến khác, query thấy commit trước trên READ COMMITTED. */
    public void requireAvailable(long exceptId, Assets assets, Instant start, Instant end, boolean starting) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT 1
                FROM {h-schema}carrier_trips
                WHERE id <> :exceptId
                    AND status IN ('OPEN', 'IN_PROGRESS')
                    AND (vehicle_id = :vehicleId OR driver_id = :driverId OR driver_user_id = :driverUserId)
                    AND ((departure_at < :rangeEnd AND end_at > :rangeStart) OR (:starting AND status = 'IN_PROGRESS'))
                LIMIT 1
                """,
                Tuple.class
            );

            query.setParameter("exceptId", exceptId);
            query.setParameter("vehicleId", assets.getVehicleId());
            query.setParameter("driverId", assets.getDriverId());
            query.setParameter("driverUserId", assets.getDriverUserId());
            query.setParameter("rangeEnd", Timestamp.from(end));
            query.setParameter("rangeStart", Timestamp.from(start));
            query.setParameter("starting", starting);

            List<?> rowValues = query.getResultList();
            Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

            if (row != null) throw new BusinessException(ErrorCode.TRIP_SCHEDULE_CONFLICT);
        }
    }

    /** Đếm và trang dùng một predicate scope; chỉ projection tối thiểu cho picker. */
    public PageResponse<TripChoiceResponse> choices(TripChoiceSearch body) {
        String from;
        String id;
        String code;
        String name;
        String capacity = "NULL::integer";
        String usable;
        switch (body.getKind()) {
            case CARRIER -> {
                from = "{h-schema}carriers owner";
                id = "owner.id";
                code = "owner.code";
                name = "owner.name";
                usable = "owner.active";
            }
            case ROUTE -> {
                from = """
                {h-schema}carrier_routes item
                JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                """;
                id = "item.id";
                code = "item.code";
                name = "CONCAT_WS(' / ',item.name_vi,item.name_en)";
                usable = "owner.active AND item.active";
            }
            case VEHICLE -> {
                from = """
                {h-schema}carrier_vehicles item
                JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type='VEHICLE_TYPE' AND vt.type='VALUE'
                """;
                id = "item.id";
                code = "item.code";
                name = "item.plate_number";
                capacity = "item.capacity";
                usable = "owner.active AND item.active AND vt.active";
            }
            case DRIVER -> {
                from = """
                {h-schema}carrier_drivers item
                JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                JOIN {h-schema}auth_users du ON du.id = item.user_id
                """;
                id = "item.id";
                code = "item.code";
                name = "item.name";
                usable = """
                owner.active
                    AND item.active
                    AND du.active
                    AND NOT du.activation_pending
                    AND NOT du.must_change_password
                    AND du.email_verified_at IS NOT NULL
                    AND EXISTS (
                        SELECT 1
                        FROM (
                            SELECT scope_id, user_id, BOOL_AND(active) AS active, MAX(version) AS version
                            FROM {h-schema}auth_user_roles
                            WHERE scope_id > 0
                            GROUP BY scope_id, user_id
                        ) sm
                        JOIN (
                            SELECT scope_id, user_id, role_code
                            FROM {h-schema}auth_user_roles
                            WHERE scope_id > 0 AND active
                        ) sr ON sr.scope_id = sm.scope_id
                            AND sr.user_id = sm.user_id
                        JOIN {h-schema}auth_user_roles ur ON ur.user_id = sr.user_id AND ur.role_code = sr.role_code
                        JOIN {h-schema}auth_roles rr ON rr.code = ur.role_code AND rr.active AND NOT rr.deleted
                        WHERE sm.scope_id = owner.scope_id AND sm.user_id = du.id AND sm.active
                    )
                """;
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        BusinessRules.require(
            body.getKind() == TripChoiceKind.CARRIER || body.getCarrierId() != null,
            ErrorCode.INVALID_REQUEST
        );
        String filter = """
        FROM %s
        WHERE %s
            AND %s
            AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            AND STRPOS(LOWER(CONCAT_WS(' ', %s, %s)), :keyword) > 0
        """.formatted(from, UserRoleRepo.visibleSql(), usable, code, name);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            bindChoice(query, body);

            Tuple row = (Tuple) query.getSingleResult();
            total = longValue(row.get(0));
        }
        ArrayList<TripChoiceResponse> result = new ArrayList<TripChoiceResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s, %s, %s, %s
                %s
                ORDER BY %s
                LIMIT :pageSize OFFSET :offset
                """.formatted(id, code, name, capacity, filter, id),
                Tuple.class
            );

            bindChoice(query, body);
            query.setParameter("pageSize", body.getSize());
            query.setParameter("offset", body.offset());

            for (Object rowValue : query.getResultList()) {
                Tuple row = (Tuple) rowValue;
                result.add(
                    new TripChoiceResponse(
                        longValue(row.get(0)),
                        stringValue(row.get(1)),
                        stringValue(row.get(2)),
                        row.get(3, Integer.class)
                    )
                );
            }
        }
        return PageResponse.of(result, body, total);
    }

    private void bindChoice(Query query, TripChoiceSearch body) {
        UserRoleRepo.bindVisible(query::setParameter);
        query.setParameter("carrierId", body.getCarrierId());
        query.setParameter("keyword", body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT));
    }
}
