package vn.vietroute.business.trip.repository;

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
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.trip.dto.request.TripCreateRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.response.TripEventResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.business.trip.repository.TripTemplateRepository.Assets;
import vn.vietroute.business.trip.repository.TripTemplateRepository.Template;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** JDBC chuyến: SQL scope trước phân trang, snapshot và lịch sử ghi cùng connection. */
@Repository
public class TripRepository extends BaseRepository {

    private static final String FROM =
        " FROM carrier_trips t JOIN carriers owner ON owner.id=t.carrier_id AND owner.scope_id=t.scope_id ";
    private static final String COLUMNS =
        "t.id,t.carrier_id,t.scope_id,owner.name AS carrier_name,owner.phone AS carrier_phone,t.route_id,t.code,t.name_vi,t.name_en,t.vehicle_id,t.plate_number,t.vehicle_type_id,t.vehicle_type_name_vi,t.vehicle_type_name_en,t.driver_id,t.driver_user_id,t.driver_name,t.driver_phone,t.departure_at,t.end_at,t.capacity,t.status,t.operation_note,t.version,(" +
        DataScopeGuard.visibleSql() +
        ") AS can_update";
    private static final String ASSIGNED = """
    AND t.status<>'DRAFT' AND t.driver_user_id=?
    AND EXISTS (SELECT 1 FROM carrier_drivers cd WHERE cd.id=t.driver_id AND cd.carrier_id=t.carrier_id
                AND cd.scope_id=t.scope_id AND cd.active AND cd.user_id=t.driver_user_id)
    AND EXISTS (SELECT 1 FROM auth_scope_memberships dm
                JOIN auth_scope_roles dr ON dr.scope_id=dm.scope_id AND dr.user_id=dm.user_id
                JOIN auth_user_roles du ON du.user_id=dr.user_id AND du.role_code=dr.role_code
                JOIN auth_roles role ON role.code=du.role_code AND role.active AND NOT role.deleted
                WHERE dm.scope_id=t.scope_id AND dm.user_id=t.driver_user_id AND dm.active AND role.code='DRIVER')
    """;

    public TripRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Count và trang cùng bộ lọc ngày xuất phát Việt Nam, không lọc lại trong RAM. */
    public PageResponse<TripResponse> page(TripSearchRequest search) {
        return page(search, Groups.TRIP, null);
    }

    /** Chỉ chuyến được giao và hồ sơ DRIVER/membership hiện hành, kể cả người gọi cũng có ADMIN. */
    public PageResponse<TripResponse> assignedPage(TripSearchRequest search, long driverUserId) {
        return page(search, Groups.DRIVER_TRIP, driverUserId);
    }

    private PageResponse<TripResponse> page(TripSearchRequest search, String resource, Long driverUserId) {
        return read(connection -> {
            String filter =
                FROM +
                " WHERE " +
                DataScopeGuard.visibleSql() +
                " AND (?::bigint IS NULL OR owner.id=?) AND (?::text IS NULL OR t.status=?) AND (?::timestamptz IS NULL OR (t.departure_at>=? AND t.departure_at<?)) AND strpos(lower(concat_ws(' ',t.code,t.name_vi,t.name_en,t.plate_number,owner.name)),?)>0" +
                (driverUserId == null ? "" : " " + ASSIGNED);
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
                bindSearch(query, 1, search, resource, driverUserId);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var result = new ArrayList<TripResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + COLUMNS + filter + " ORDER BY t.departure_at DESC,t.id DESC LIMIT ? OFFSET ?"
                )
            ) {
                int at = DataScopeGuard.bindVisible(query, 1, resource, PermissionAction.UPDATE);
                at = bindSearch(query, at, search, resource, driverUserId);
                query.setInt(at++, search.getSize());
                query.setLong(at, search.offset());
                try (var row = query.executeQuery()) {
                    while (row.next()) result.add(map(row, List.of(), List.of()));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    private int bindSearch(PreparedStatement query, int at, TripSearchRequest body, String resource, Long driverUserId)
        throws SQLException {
        at = DataScopeGuard.bindVisible(query, at, resource, PermissionAction.READ);
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
    public TripResponse find(Connection connection, long id, PermissionAction action, boolean lock, boolean detail)
        throws SQLException {
        return find(connection, id, action, lock, detail, Groups.TRIP, null);
    }

    /** Tài xế ngoài phân công hiện hành nhận 404 trước đọc điểm/lịch sử hoặc khóa mutation. */
    public TripResponse assigned(
        Connection connection,
        long id,
        PermissionAction action,
        boolean lock,
        long driverUserId
    ) throws SQLException {
        return find(connection, id, action, lock, true, Groups.DRIVER_TRIP, driverUserId);
    }

    private TripResponse find(
        Connection connection,
        long id,
        PermissionAction action,
        boolean lock,
        boolean detail,
        String resource,
        Long driverUserId
    ) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT " +
                    COLUMNS +
                    FROM +
                    " WHERE t.id=? AND " +
                    DataScopeGuard.visibleSql() +
                    (driverUserId == null ? "" : ASSIGNED) +
                    (lock ? " FOR UPDATE OF t" : "")
            )
        ) {
            int at = DataScopeGuard.bindVisible(query, 1, resource, PermissionAction.UPDATE);
            query.setLong(at++, id);
            at = DataScopeGuard.bindVisible(query, at, resource, action);
            if (driverUserId != null) query.setLong(at, driverUserId);
            try (var row = query.executeQuery()) {
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
        var result = new ArrayList<TripStopResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT id,location_id,province_id,stop_order,name_vi,name_en,address,latitude,longitude,scheduled_arrival_at,scheduled_departure_at,actual_arrival_at,actual_departure_at,cumulative_fare,pickup_allowed,dropoff_allowed FROM trip_stops WHERE trip_id=? ORDER BY stop_order"
            )
        ) {
            query.setLong(1, tripId);
            try (var row = query.executeQuery()) {
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
        var result = new ArrayList<TripEventResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT id,event_type,stop_order,note,created_at FROM (SELECT id,event_type,stop_order,note,created_at FROM trip_events WHERE trip_id=? ORDER BY id DESC LIMIT 100) e ORDER BY id"
            )
        ) {
            query.setLong(1, tripId);
            try (var row = query.executeQuery()) {
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
        TripCreateRequest body,
        Template template,
        Assets assets,
        Instant end,
        long actor
    ) throws SQLException {
        long id;
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carrier_trips(carrier_id,scope_id,route_id,vehicle_id,driver_id,driver_user_id,code,name_vi,name_en,plate_number,vehicle_type_id,vehicle_type_name_vi,vehicle_type_name_en,driver_name,driver_phone,departure_at,end_at,capacity,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, template.carrierId());
            query.setLong(2, template.scopeId());
            query.setLong(3, template.id());
            query.setLong(4, assets.vehicleId());
            query.setLong(5, assets.driverId());
            query.setLong(6, assets.driverUserId());
            query.setString(7, body.getCode());
            query.setString(8, template.nameVi());
            query.setString(9, template.nameEn());
            query.setString(10, assets.plate());
            query.setLong(11, assets.typeId());
            query.setString(12, assets.typeVi());
            query.setString(13, assets.typeEn());
            query.setString(14, assets.driverName());
            query.setString(15, assets.driverPhone());
            query.setTimestamp(16, Timestamp.from(body.getDepartureAt()));
            query.setTimestamp(17, Timestamp.from(end));
            query.setInt(18, body.getCapacity());
            query.setLong(19, actor);
            query.setLong(20, actor);
            try (var row = query.executeQuery()) {
                row.next();
                id = row.getLong(1);
            }
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO trip_stops(trip_id,carrier_id,scope_id,location_id,province_id,stop_order,name_vi,name_en,address,latitude,longitude,scheduled_arrival_at,scheduled_departure_at,cumulative_fare,pickup_allowed,dropoff_allowed,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
        ) {
            for (var stop : template.stops()) {
                query.setLong(1, id);
                query.setLong(2, template.carrierId());
                query.setLong(3, template.scopeId());
                query.setLong(4, stop.locationId());
                query.setLong(5, stop.provinceId());
                query.setInt(6, stop.order());
                query.setString(7, stop.nameVi());
                query.setString(8, stop.nameEn());
                query.setString(9, stop.address());
                query.setBigDecimal(10, stop.latitude());
                query.setBigDecimal(11, stop.longitude());
                query.setTimestamp(12, Timestamp.from(body.getDepartureAt().plusSeconds(stop.arrival() * 60L)));
                query.setTimestamp(13, Timestamp.from(body.getDepartureAt().plusSeconds(stop.departure() * 60L)));
                query.setBigDecimal(14, stop.fare());
                query.setBoolean(15, stop.pickup());
                query.setBoolean(16, stop.dropoff());
                query.setLong(17, actor);
                query.setLong(18, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO trip_segments(trip_id,carrier_id,scope_id,segment_order,capacity,updated_by) VALUES(?,?,?,?,?,?)"
            )
        ) {
            for (int order = 1; order < template.stops().size(); order++) {
                query.setLong(1, id);
                query.setLong(2, template.carrierId());
                query.setLong(3, template.scopeId());
                query.setInt(4, order);
                query.setInt(5, body.getCapacity());
                query.setLong(6, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
        event(connection, id, template.carrierId(), template.scopeId(), "CREATED", null, "", actor);
        return id;
    }

    /** Trip đã khóa; khóa segment theo thứ tự tăng và lấy mức đặt cao nhất trước khi đổi capacity. */
    public int peakReserved(Connection connection, long id) throws SQLException {
        int peak = 0;
        try (
            var query = connection.prepareStatement(
                "SELECT reserved_seats FROM trip_segments WHERE trip_id=? ORDER BY segment_order FOR UPDATE"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                while (row.next()) peak = Math.max(peak, row.getInt(1));
            }
        }
        return peak;
    }

    /** Kiểm active của các địa điểm snapshot gốc, kể cả mẫu tuyến đã thay sang địa điểm khác. */
    public void requireStopsActive(Connection connection, long id) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT l.active AND p.active FROM trip_stops s JOIN location_places l ON l.id=s.location_id JOIN location_provinces p ON p.id=s.province_id WHERE s.trip_id=? ORDER BY s.stop_order FOR SHARE OF l,p"
            )
        ) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
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
            var query = connection.prepareStatement(
                "UPDATE carrier_trips SET vehicle_id=?,driver_id=?,driver_user_id=?,plate_number=?,vehicle_type_id=?,vehicle_type_name_vi=?,vehicle_type_name_en=?,driver_name=?,driver_phone=?,capacity=?,departure_at=?,end_at=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setLong(1, assets.vehicleId());
            query.setLong(2, assets.driverId());
            query.setLong(3, assets.driverUserId());
            query.setString(4, assets.plate());
            query.setLong(5, assets.typeId());
            query.setString(6, assets.typeVi());
            query.setString(7, assets.typeEn());
            query.setString(8, assets.driverName());
            query.setString(9, assets.driverPhone());
            query.setInt(10, capacity);
            query.setTimestamp(11, Timestamp.from(departure));
            query.setTimestamp(12, Timestamp.from(end));
            query.setLong(13, actor);
            query.setLong(14, trip.id());
            query.setLong(15, trip.carrierId());
            query.setLong(16, trip.scopeId());
            query.setLong(17, trip.version());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        long delta = departure.getEpochSecond() - trip.departureAt().getEpochSecond();
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_stops SET scheduled_arrival_at=scheduled_arrival_at+(?*INTERVAL '1 second'),scheduled_departure_at=scheduled_departure_at+(?*INTERVAL '1 second'),updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=?"
            )
        ) {
            query.setLong(1, delta);
            query.setLong(2, delta);
            query.setLong(3, actor);
            query.setLong(4, trip.id());
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_segments SET capacity=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=?"
            )
        ) {
            query.setInt(1, capacity);
            query.setLong(2, actor);
            query.setLong(3, trip.id());
            query.executeUpdate();
        }
    }

    /** Mọi state transition đều tăng version; caller kiểm đồ thị trạng thái trước khi ghi. */
    public void state(Connection connection, TripResponse trip, TripStatus status, String note, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carrier_trips SET status=?,operation_note=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setString(1, status.name());
            query.setString(2, note);
            query.setLong(3, actor);
            query.setLong(4, trip.id());
            query.setLong(5, trip.carrierId());
            query.setLong(6, trip.scopeId());
            query.setLong(7, trip.version());
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
            var query = connection.prepareStatement(
                "INSERT INTO trip_events(trip_id,carrier_id,scope_id,event_type,stop_order,note,created_by) VALUES(?,?,?,?,?,?,?)"
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
        var value = row.getTimestamp(at);
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
}
