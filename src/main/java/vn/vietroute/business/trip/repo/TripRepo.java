package vn.vietroute.business.trip.repo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.validation.BusinessRules;

/** JDBC chuyến: SQL scope trước phân trang, snapshot và lịch sử ghi cùng connection. */
@Repository
public class TripRepo extends BaseRepository {

    private static final String FROM = """
    FROM carrier_trips t
    JOIN carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
    """;
    private static final String COLUMNS = """
    t.id, t.carrier_id, t.scope_id, owner.name AS carrier_name, owner.phone AS carrier_phone, t.route_id,
        t.code, t.name_vi, t.name_en, t.vehicle_id, t.plate_number, t.vehicle_type_id, t.vehicle_type_name_vi,
        t.vehicle_type_name_en, t.driver_id, t.driver_user_id, t.driver_name, t.driver_phone, t.departure_at,
        t.end_at, t.capacity, t.status, t.operation_note, t.version, (%s) AS can_update
    """.formatted(UserRoleRepo.visibleSql());
    private static final String ASSIGNED = """
        AND t.status <> 'DRAFT'
        AND t.driver_user_id = ?
        AND EXISTS (
            SELECT 1
            FROM carrier_drivers cd
            WHERE cd.id = t.driver_id AND cd.carrier_id = t.carrier_id AND cd.scope_id = t.scope_id
                AND cd.active
                AND cd.user_id = t.driver_user_id
        )
        AND EXISTS (
            SELECT 1
            FROM (
                SELECT scope_id, user_id, bool_and(active) AS active, max(version) AS version
                FROM auth_user_roles
                WHERE scope_id > 0
                GROUP BY scope_id, user_id
            ) dm
            JOIN (
                SELECT scope_id, user_id, role_code
                FROM auth_user_roles
                WHERE scope_id > 0 AND active
            ) dr ON dr.scope_id = dm.scope_id
                AND dr.user_id = dm.user_id
            JOIN auth_user_roles du ON du.user_id = dr.user_id AND du.role_code = dr.role_code
            JOIN auth_roles role ON role.code = du.role_code AND role.active AND NOT role.deleted
            WHERE dm.scope_id = t.scope_id AND dm.user_id = t.driver_user_id AND dm.active
        )
    """;

    public TripRepo(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Count và trang cùng bộ lọc ngày xuất phát Việt Nam, không lọc lại trong RAM. */
    public PageResponse<TripResponse> page(TripSearch search) {
        return page(search, null);
    }

    /** Chỉ chuyến được giao và hồ sơ DRIVER/membership hiện hành, kể cả người gọi cũng có ADMIN. */
    public PageResponse<TripResponse> assignedPage(TripSearch search, long driverUserId) {
        return page(search, driverUserId);
    }

    private PageResponse<TripResponse> page(TripSearch search, Long driverUserId) {
        return read(connection -> {
            String filter = """
            %s
            WHERE %s
                AND (?::bigint IS NULL OR owner.id = ?)
                AND (?::text IS NULL OR t.status = ?)
                AND (?::timestamptz IS NULL OR (t.departure_at >= ? AND t.departure_at < ?))
                AND strpos(lower(concat_ws(' ', t.code, t.name_vi, t.name_en, t.plate_number, owner.name)),
                ?) > 0
            %s
            """.formatted(FROM, UserRoleRepo.visibleSql(), driverUserId == null ? "" : " " + ASSIGNED);
            long total;
            try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
                bindSearch(query, 1, search, driverUserId);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            ArrayList<TripResponse> result = new ArrayList<TripResponse>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT %s
                    %s
                    ORDER BY t.departure_at DESC, t.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(COLUMNS, filter)
                )
            ) {
                int at = UserRoleRepo.bindVisible(query, 1, "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID);
                at = bindSearch(query, at, search, driverUserId);
                query.setInt(at++, search.getSize());
                query.setLong(at, search.offset());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) result.add(map(row, List.of(), List.of()));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    private int bindSearch(PreparedStatement query, int at, TripSearch body, Long driverUserId) throws SQLException {
        at = UserRoleRepo.bindVisible(query, at);
        query.setObject(at++, body.getCarrierId());
        query.setObject(at++, body.getCarrierId());
        String status = body.getStatus() == null ? null : body.getStatus().name();
        query.setString(at++, status);
        query.setString(at++, status);
        Timestamp start =
            body.getDate() == null
                ? null
                : Timestamp.from(body.getDate().atStartOfDay(ZoneId.of(Business.TIME_ZONE)).toInstant());
        Timestamp end =
            body.getDate() == null
                ? null
                : Timestamp.from(body.getDate().plusDays(1).atStartOfDay(ZoneId.of(Business.TIME_ZONE)).toInstant());
        query.setTimestamp(at++, start);
        query.setTimestamp(at++, start);
        query.setTimestamp(at++, end);
        query.setString(at++, body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT));
        if (driverUserId != null) query.setLong(at++, driverUserId);
        return at;
    }

    /** ID ngoài scope trả 404; UPDATE khóa trip trước khi service khóa tài nguyên phân công. */
    public TripResponse find(Connection connection, long id, boolean lock, boolean detail) throws SQLException {
        return find(connection, id, lock, detail, null);
    }

    /** Tài xế ngoài phân công hiện hành nhận 404 trước đọc điểm/lịch sử hoặc khóa mutation. */
    public TripResponse assigned(Connection connection, long id, boolean lock, long driverUserId) throws SQLException {
        return find(connection, id, lock, true, driverUserId);
    }

    private TripResponse find(Connection connection, long id, boolean lock, boolean detail, Long driverUserId)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s
                %s
                WHERE t.id = ?
                    AND %s
                %s
                %s
                """.formatted(
                    COLUMNS,
                    FROM,
                    UserRoleRepo.visibleSql(),
                    driverUserId == null ? "" : ASSIGNED,
                    lock ? " FOR UPDATE OF t" : ""
                )
            )
        ) {
            int at = UserRoleRepo.bindVisible(query, 1, "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID);
            query.setLong(at++, id);
            at = UserRoleRepo.bindVisible(query, at);
            if (driverUserId != null) query.setLong(at, driverUserId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(
                    row,
                    detail ? stops(connection, id) : List.of(),
                    detail ? events(connection, id) : List.of()
                );
            }
        }
    }

    /** Chỉ caller đã xác minh trip/scope mới đọc snapshot điểm; thời gian đổi Timestamp tại ranh JDBC. */
    public List<TripStopResponse> stops(Connection connection, long tripId) throws SQLException {
        ArrayList<TripStopResponse> result = new ArrayList<TripStopResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT id, location_id, province_id, stop_order, name_vi, name_en, address, latitude, longitude,
                    scheduled_arrival_at, scheduled_departure_at, actual_arrival_at, actual_departure_at,
                    cumulative_fare, pickup_allowed, dropoff_allowed
                FROM trip_stops
                WHERE trip_id = ?
                ORDER BY stop_order
                """
            )
        ) {
            query.setLong(1, tripId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new TripStopResponse(
                            row.getLong(1),
                            row.getLong(2),
                            row.getLong(3),
                            row.getInt(4),
                            row.getString(5),
                            row.getString(6),
                            row.getString(7),
                            row.getBigDecimal(8),
                            row.getBigDecimal(9),
                            instant(row, 10),
                            instant(row, 11),
                            instant(row, 12),
                            instant(row, 13),
                            row.getBigDecimal(14),
                            row.getBoolean(15),
                            row.getBoolean(16)
                        )
                    );
            }
        }
        return List.copyOf(result);
    }

    /** Lịch sử mới nhất giới hạn 100, xếp thời gian thuận cho UI, không join email/account credentials. */
    private List<TripEventResponse> events(Connection connection, long tripId) throws SQLException {
        ArrayList<TripEventResponse> result = new ArrayList<TripEventResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT id, event_type, stop_order, note, created_at
                FROM (
                    SELECT id, event_type, stop_order, note, created_at
                    FROM trip_events
                    WHERE trip_id = ?
                    ORDER BY id DESC
                    LIMIT 100
                ) e
                ORDER BY id
                """
            )
        ) {
            query.setLong(1, tripId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new TripEventResponse(
                            row.getLong(1),
                            row.getString(2),
                            row.getObject(3, Integer.class),
                            row.getString(4),
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
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_trips(carrier_id, scope_id, route_id, vehicle_id, driver_id, driver_user_id,
                    code, name_vi, name_en, plate_number, vehicle_type_id, vehicle_type_name_vi,
                    vehicle_type_name_en, driver_name, driver_phone, departure_at, end_at, capacity, created_by,
                    updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """
            )
        ) {
            query.setLong(1, template.getCarrierId());
            query.setLong(2, template.getScopeId());
            query.setLong(3, template.getId());
            query.setLong(4, assets.getVehicleId());
            query.setLong(5, assets.getDriverId());
            query.setLong(6, assets.getDriverUserId());
            query.setString(7, body.getCode());
            query.setString(8, template.getNameVi());
            query.setString(9, template.getNameEn());
            query.setString(10, assets.getPlate());
            query.setLong(11, assets.getTypeId());
            query.setString(12, assets.getTypeVi());
            query.setString(13, assets.getTypeEn());
            query.setString(14, assets.getDriverName());
            query.setString(15, assets.getDriverPhone());
            query.setTimestamp(16, Timestamp.from(body.getDepartureAt()));
            query.setTimestamp(17, Timestamp.from(end));
            query.setInt(18, body.getCapacity());
            query.setLong(19, actor);
            query.setLong(20, actor);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                id = row.getLong(1);
            }
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO trip_stops(trip_id, carrier_id, scope_id, location_id, province_id, stop_order,
                    name_vi, name_en, address, latitude, longitude, scheduled_arrival_at, scheduled_departure_at,
                    cumulative_fare, pickup_allowed, dropoff_allowed, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            for (SourceStop stop : template.getStops()) {
                query.setLong(1, id);
                query.setLong(2, template.getCarrierId());
                query.setLong(3, template.getScopeId());
                query.setLong(4, stop.getLocationId());
                query.setLong(5, stop.getProvinceId());
                query.setInt(6, stop.getOrder());
                query.setString(7, stop.getNameVi());
                query.setString(8, stop.getNameEn());
                query.setString(9, stop.getAddress());
                query.setBigDecimal(10, stop.getLatitude());
                query.setBigDecimal(11, stop.getLongitude());
                query.setTimestamp(12, Timestamp.from(body.getDepartureAt().plusSeconds(stop.getArrival() * 60L)));
                query.setTimestamp(13, Timestamp.from(body.getDepartureAt().plusSeconds(stop.getDeparture() * 60L)));
                query.setBigDecimal(14, stop.getFare());
                query.setBoolean(15, stop.isPickup());
                query.setBoolean(16, stop.isDropoff());
                query.setLong(17, actor);
                query.setLong(18, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO trip_segments(trip_id, carrier_id, scope_id, segment_order, capacity, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            for (int order = 1; order < template.getStops().size(); order++) {
                query.setLong(1, id);
                query.setLong(2, template.getCarrierId());
                query.setLong(3, template.getScopeId());
                query.setInt(4, order);
                query.setInt(5, body.getCapacity());
                query.setLong(6, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
        event(connection, id, template.getCarrierId(), template.getScopeId(), "CREATED", null, "", actor);
        return id;
    }

    /** Trip đã khóa; khóa segment theo thứ tự tăng và lấy mức đặt cao nhất trước khi đổi capacity. */
    public int peakReserved(Connection connection, long id) throws SQLException {
        int peak = 0;
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT reserved_seats
                FROM trip_segments
                WHERE trip_id = ?
                ORDER BY segment_order
                FOR UPDATE
                """
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) peak = Math.max(peak, row.getInt(1));
            }
        }
        return peak;
    }

    /** Kiểm active của các địa điểm snapshot gốc, kể cả mẫu tuyến đã thay sang địa điểm khác. */
    public void requireStopsActive(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT l.active
                    AND p.active
                FROM trip_stops s
                JOIN location_places l ON l.id = s.location_id
                JOIN location_provinces p ON p.id = s.province_id
                WHERE s.trip_id = ?
                ORDER BY s.stop_order
                FOR SHARE OF l, p
                """
            )
        ) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) if (!rows.getBoolean(1)) throw new BusinessException(ErrorCode.TRIP_STATE_CONFLICT);
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
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE carrier_trips
                SET vehicle_id = ?, driver_id = ?, driver_user_id = ?, plate_number = ?, vehicle_type_id = ?,
                    vehicle_type_name_vi = ?, vehicle_type_name_en = ?, driver_name = ?, driver_phone = ?,
                    capacity = ?, departure_at = ?, end_at = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ? AND carrier_id = ? AND scope_id = ? AND version = ?
                """
            )
        ) {
            query.setLong(1, assets.getVehicleId());
            query.setLong(2, assets.getDriverId());
            query.setLong(3, assets.getDriverUserId());
            query.setString(4, assets.getPlate());
            query.setLong(5, assets.getTypeId());
            query.setString(6, assets.getTypeVi());
            query.setString(7, assets.getTypeEn());
            query.setString(8, assets.getDriverName());
            query.setString(9, assets.getDriverPhone());
            query.setInt(10, capacity);
            query.setTimestamp(11, Timestamp.from(departure));
            query.setTimestamp(12, Timestamp.from(end));
            query.setLong(13, actor);
            query.setLong(14, trip.getId());
            query.setLong(15, trip.getCarrierId());
            query.setLong(16, trip.getScopeId());
            query.setLong(17, trip.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        long delta = departure.getEpochSecond() - trip.getDepartureAt().getEpochSecond();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE trip_stops
                SET scheduled_arrival_at = scheduled_arrival_at + (? * INTERVAL '1 second'),
                    scheduled_departure_at = scheduled_departure_at + (? * INTERVAL '1 second'), updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE trip_id = ?
                """
            )
        ) {
            query.setLong(1, delta);
            query.setLong(2, delta);
            query.setLong(3, actor);
            query.setLong(4, trip.getId());
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE trip_segments
                SET capacity = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE trip_id = ?
                """
            )
        ) {
            query.setInt(1, capacity);
            query.setLong(2, actor);
            query.setLong(3, trip.getId());
            query.executeUpdate();
        }
    }

    /** Mọi state transition đều tăng version; caller kiểm đồ thị trạng thái trước khi ghi. */
    public void state(Connection connection, TripResponse trip, TripStatus status, String note, long actor)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE carrier_trips
                SET status = ?, operation_note = ?, updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = ? AND carrier_id = ? AND scope_id = ? AND version = ?
                """
            )
        ) {
            query.setString(1, status.name());
            query.setString(2, note);
            query.setLong(3, actor);
            query.setLong(4, trip.getId());
            query.setLong(5, trip.getCarrierId());
            query.setLong(6, trip.getScopeId());
            query.setLong(7, trip.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Nếu ghi lịch sử lỗi, exception đi ra ngoài để rollback cả mutation. */
    public void event(
        Connection connection,
        long tripId,
        long carrierId,
        long scopeId,
        String type,
        Integer order,
        String note,
        long actor
    ) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO trip_events(trip_id, carrier_id, scope_id, event_type, stop_order, note, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            query.setLong(1, tripId);
            query.setLong(2, carrierId);
            query.setLong(3, scopeId);
            query.setString(4, type);
            query.setObject(5, order);
            query.setString(6, note);
            query.setLong(7, actor);
            query.executeUpdate();
        }
    }

    private static Instant instant(ResultSet row, int at) throws SQLException {
        Timestamp value = row.getTimestamp(at);
        return value == null ? null : value.toInstant();
    }

    private static TripResponse map(ResultSet row, List<TripStopResponse> stops, List<TripEventResponse> events)
        throws SQLException {
        return new TripResponse(
            row.getLong("id"),
            row.getLong("carrier_id"),
            row.getLong("scope_id"),
            row.getString("carrier_name"),
            row.getString("carrier_phone"),
            row.getLong("route_id"),
            row.getString("code"),
            row.getString("name_vi"),
            row.getString("name_en"),
            row.getLong("vehicle_id"),
            row.getString("plate_number"),
            row.getLong("vehicle_type_id"),
            row.getString("vehicle_type_name_vi"),
            row.getString("vehicle_type_name_en"),
            row.getLong("driver_id"),
            row.getLong("driver_user_id"),
            row.getString("driver_name"),
            row.getString("driver_phone"),
            row.getTimestamp("departure_at").toInstant(),
            row.getTimestamp("end_at").toInstant(),
            row.getInt("capacity"),
            TripStatus.valueOf(row.getString("status")),
            row.getString("operation_note"),
            row.getLong("version"),
            row.getBoolean("can_update"),
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
    public Template template(Connection connection, long routeId, Long carrierId, boolean lock) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT id, carrier_id, scope_id, name_vi, name_en, active
                FROM carrier_routes
                WHERE id = ?
                    AND (?::bigint IS NULL OR carrier_id = ?)
                %s
                """.formatted(lock ? " FOR SHARE" : "")
            )
        ) {
            query.setLong(1, routeId);
            query.setObject(2, carrierId);
            query.setObject(3, carrierId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return new Template(
                    row.getLong(1),
                    row.getLong(2),
                    row.getLong(3),
                    row.getString(4),
                    row.getString(5),
                    row.getBoolean(6),
                    lock ? sourceStops(connection, routeId) : List.of()
                );
            }
        }
    }

    /** Khóa chia sẻ địa điểm/tỉnh để snapshot và kiểm active thuộc cùng transaction. */
    private List<SourceStop> sourceStops(Connection connection, long routeId) throws SQLException {
        ArrayList<SourceStop> result = new ArrayList<SourceStop>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT l.id, l.province_id, s.stop_order, l.name_vi, l.name_en, l.address, l.latitude, l.longitude,
                    s.arrival_offset_minutes, s.departure_offset_minutes, s.cumulative_fare,
                    s.pickup_allowed, s.dropoff_allowed,
                    l.active
                    AND p.active AS usable
                FROM carrier_route_stops s
                JOIN location_places l ON l.id = s.location_id
                JOIN location_provinces p ON p.id = l.province_id
                WHERE s.route_id = ?
                ORDER BY s.stop_order
                FOR SHARE OF l, p
                """
            )
        ) {
            query.setLong(1, routeId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new SourceStop(
                            row.getLong(1),
                            row.getLong(2),
                            row.getInt(3),
                            row.getString(4),
                            row.getString(5),
                            row.getString(6),
                            row.getBigDecimal(7),
                            row.getBigDecimal(8),
                            row.getInt(9),
                            row.getInt(10),
                            row.getBigDecimal(11),
                            row.getBoolean(12),
                            row.getBoolean(13),
                            row.getBoolean(14)
                        )
                    );
            }
        }
        BusinessRules.require(result.size() >= 2 && result.size() <= 32, ErrorCode.TRIP_INVALID);
        return List.copyOf(result);
    }

    /** Khóa xe rồi hồ sơ tài xế, thứ tự thống nhất để các chuyến cạnh tranh cùng tài nguyên được tuần tự hóa. */
    public Assets assets(Connection connection, long carrierId, long scopeId, long vehicleId, long driverId)
        throws SQLException {
        String plate;
        long typeId;
        String typeVi;
        String typeEn;
        int capacity;
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT v.plate_number, v.vehicle_type_id, t.name_vi, t.name_en, v.capacity
                FROM carrier_vehicles v
                JOIN catalog_items t ON t.id = v.vehicle_type_id
                    AND t.type = 'VEHICLE_TYPE'
                    AND t.active
                WHERE v.id = ? AND v.carrier_id = ? AND v.scope_id = ? AND v.active
                FOR UPDATE OF v
                FOR SHARE OF t
                """
            )
        ) {
            query.setLong(1, vehicleId);
            query.setLong(2, carrierId);
            query.setLong(3, scopeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.TRIP_INVALID);
                plate = row.getString(1);
                typeId = row.getLong(2);
                typeVi = row.getString(3);
                typeEn = row.getString(4);
                capacity = row.getInt(5);
            }
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT user_id, name, phone
                FROM carrier_drivers
                WHERE id = ? AND carrier_id = ? AND scope_id = ? AND active AND user_id IS NOT NULL
                FOR UPDATE
                """
            )
        ) {
            query.setLong(1, driverId);
            query.setLong(2, carrierId);
            query.setLong(3, scopeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.TRIP_INVALID);
                return new Assets(
                    vehicleId,
                    driverId,
                    row.getLong(1),
                    plate,
                    typeId,
                    typeVi,
                    typeEn,
                    capacity,
                    row.getString(2),
                    row.getString(3)
                );
            }
        }
    }

    /** Gọi sau khóa assets; không khóa chuyến khác, query thấy commit trước trên READ COMMITTED. */
    public void requireAvailable(
        Connection connection,
        long exceptId,
        Assets assets,
        Instant start,
        Instant end,
        boolean starting
    ) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT 1
                FROM carrier_trips
                WHERE id <> ?
                    AND status IN ('OPEN', 'IN_PROGRESS')
                    AND (vehicle_id = ? OR driver_id = ? OR driver_user_id = ?)
                    AND ((departure_at < ? AND end_at > ?) OR (? AND status = 'IN_PROGRESS'))
                LIMIT 1
                """
            )
        ) {
            query.setLong(1, exceptId);
            query.setLong(2, assets.getVehicleId());
            query.setLong(3, assets.getDriverId());
            query.setLong(4, assets.getDriverUserId());
            query.setTimestamp(5, Timestamp.from(end));
            query.setTimestamp(6, Timestamp.from(start));
            query.setBoolean(7, starting);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.TRIP_SCHEDULE_CONFLICT);
            }
        }
    }

    /** Đếm và trang dùng một predicate scope; chỉ projection tối thiểu cho picker. */
    public PageResponse<TripChoiceResponse> choices(Connection connection, TripChoiceSearch body) throws SQLException {
        String from;
        String id;
        String code;
        String name;
        String capacity = "NULL::integer";
        String usable;
        switch (body.getKind()) {
            case CARRIER -> {
                from = "carriers owner";
                id = "owner.id";
                code = "owner.code";
                name = "owner.name";
                usable = "owner.active";
            }
            case ROUTE -> {
                from = """
                carrier_routes item
                JOIN carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                """;
                id = "item.id";
                code = "item.code";
                name = "concat_ws(' / ',item.name_vi,item.name_en)";
                usable = "owner.active AND item.active";
            }
            case VEHICLE -> {
                from = """
                carrier_vehicles item
                JOIN carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                JOIN catalog_items vt ON vt.id = item.vehicle_type_id
                """;
                id = "item.id";
                code = "item.code";
                name = "item.plate_number";
                capacity = "item.capacity";
                usable = "owner.active AND item.active AND vt.active AND vt.type='VEHICLE_TYPE'";
            }
            case DRIVER -> {
                from = """
                carrier_drivers item
                JOIN carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
                JOIN auth_users du ON du.id = item.user_id
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
                            SELECT scope_id, user_id, bool_and(active) AS active, max(version) AS version
                            FROM auth_user_roles
                            WHERE scope_id > 0
                            GROUP BY scope_id, user_id
                        ) sm
                        JOIN (
                            SELECT scope_id, user_id, role_code
                            FROM auth_user_roles
                            WHERE scope_id > 0 AND active
                        ) sr ON sr.scope_id = sm.scope_id
                            AND sr.user_id = sm.user_id
                        JOIN auth_user_roles ur ON ur.user_id = sr.user_id AND ur.role_code = sr.role_code
                        JOIN auth_roles rr ON rr.code = ur.role_code AND rr.active AND NOT rr.deleted
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
            AND (?::bigint IS NULL OR owner.id = ?)
            AND strpos(lower(concat_ws(' ', %s, %s)), ?) > 0
        """.formatted(from, UserRoleRepo.visibleSql(), usable, code, name);
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
            bindChoice(query, body);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<TripChoiceResponse> result = new ArrayList<TripChoiceResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s, %s, %s, %s
                %s
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(id, code, name, capacity, filter, id)
            )
        ) {
            int at = bindChoice(query, body);
            query.setInt(at++, body.getSize());
            query.setLong(at, body.offset());
            try (ResultSet row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new TripChoiceResponse(
                            row.getLong(1),
                            row.getString(2),
                            row.getString(3),
                            row.getObject(4, Integer.class)
                        )
                    );
            }
        }
        return PageResponse.of(result, body, total);
    }

    private int bindChoice(PreparedStatement query, TripChoiceSearch body) throws SQLException {
        int at = UserRoleRepo.bindVisible(query, 1);
        query.setObject(at++, body.getCarrierId());
        query.setObject(at++, body.getCarrierId());
        query.setString(at++, body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT));
        return at;
    }
}
