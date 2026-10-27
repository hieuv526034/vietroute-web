package vn.vietroute.business.booking.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.booking.dto.request.BookingCreateRequest;
import vn.vietroute.business.booking.dto.request.BookingSearchRequest;
import vn.vietroute.business.booking.dto.request.BookingTripSearchRequest;
import vn.vietroute.business.booking.dto.response.BookingEventResponse;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.dto.response.DriverPassengerResponse;
import vn.vietroute.business.booking.entity.BookingStatus;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** SQL đơn luôn owner hoặc scope trong predicate; trường PII không dùng cho lookup công khai. */
@Repository
public class BookingRepository extends BaseRepository {

    public record Trip(
        long id,
        long carrierId,
        long scopeId,
        TripStatus status,
        Instant departureAt,
        boolean accepting
    ) {}

    public record Stop(
        long id,
        int order,
        BigDecimal fare,
        boolean pickup,
        boolean dropoff,
        Instant departure,
        Instant actualDeparture
    ) {}

    public record Row(BookingResponse response, Long customerId, long createdBy, UUID requestKey) {}

    private static final String FROM =
        " FROM bookings b JOIN carrier_trips t ON t.id=b.trip_id AND t.carrier_id=b.carrier_id AND t.scope_id=b.scope_id JOIN carriers owner ON owner.id=b.carrier_id AND owner.scope_id=b.scope_id JOIN trip_stops pick ON pick.id=b.pickup_stop_id AND pick.trip_id=b.trip_id JOIN trip_stops dropoff ON dropoff.id=b.dropoff_stop_id AND dropoff.trip_id=b.trip_id ";
    private static final String COLUMNS =
        "b.*,t.code AS trip_code,owner.name AS carrier_name,owner.phone AS carrier_phone,pick.name_vi AS pickup_vi,pick.name_en AS pickup_en,dropoff.name_vi AS dropoff_vi,dropoff.name_en AS dropoff_en,pick.scheduled_departure_at AS pickup_at,dropoff.scheduled_arrival_at AS dropoff_at";

    public BookingRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Chỉ gọi sau trips.assigned trong cùng transaction; SQL không trả PENDING/PII không phục vụ chuyến. */
    public PageResponse<DriverPassengerResponse> passengers(Connection connection, long tripId, PageRequest page)
        throws SQLException {
        String filter = FROM + " WHERE b.trip_id=? AND b.status IN('CONFIRMED','COMPLETED')";
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
            query.setLong(1, tripId);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var result = new ArrayList<DriverPassengerResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT b.code,b.pickup_order,b.dropoff_order,pick.name_vi,pick.name_en,dropoff.name_vi,dropoff.name_en,b.seats,b.contact_name,b.contact_phone,b.status" +
                    filter +
                    " ORDER BY b.pickup_order,b.dropoff_order,b.id LIMIT ? OFFSET ?"
            )
        ) {
            query.setLong(1, tripId);
            query.setInt(2, page.getSize());
            query.setLong(3, page.offset());
            try (var row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new DriverPassengerResponse(
                            row.getString(1),
                            row.getInt(2),
                            row.getInt(3),
                            row.getString(4),
                            row.getString(5),
                            row.getString(6),
                            row.getString(7),
                            row.getInt(8),
                            row.getString(9),
                            row.getString(10),
                            row.getString(11)
                        )
                    );
            }
        }
        return PageResponse.of(result, page, total);
    }

    /** Picker PHONE chỉ hiện trip có CREATE trong scope, không cần TRIP_READ và không trả hồ sơ staff. */
    public PageResponse<BookingTripChoiceResponse> choices(Connection connection, BookingTripSearchRequest body)
        throws SQLException {
        String filter =
            " FROM carrier_trips t JOIN carriers owner ON owner.id=t.carrier_id AND owner.scope_id=t.scope_id WHERE " +
            DataScopeGuard.visibleSql() +
            " AND owner.active AND (t.status='IN_PROGRESS' OR (t.status='OPEN' AND t.departure_at>CURRENT_TIMESTAMP)) AND strpos(lower(concat_ws(' ',t.code,t.name_vi,t.name_en,owner.name)),?)>0";
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.BOOKING, PermissionAction.CREATE);
            query.setString(at, text);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var result = new ArrayList<BookingTripChoiceResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT t.id,t.code,owner.name,t.name_vi,t.name_en,t.departure_at" +
                    filter +
                    " ORDER BY t.departure_at,t.id LIMIT ? OFFSET ?"
            )
        ) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.BOOKING, PermissionAction.CREATE);
            query.setString(at++, text);
            query.setInt(at++, body.getSize());
            query.setLong(at, body.offset());
            try (var row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new BookingTripChoiceResponse(
                            row.getLong(1),
                            row.getString(2),
                            row.getString(3),
                            row.getString(4),
                            row.getString(5),
                            row.getTimestamp(6).toInstant()
                        )
                    );
            }
        }
        return PageResponse.of(result, body, total);
    }

    /** Không có scope staff thay thế owner; actor đã được kiểm CUSTOMER/ready ở cùng connection. */
    public void requireCustomer(Connection connection, long user, long tokenVersion, boolean lock) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT u.id FROM auth_users u WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL AND EXISTS(SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted WHERE ur.user_id=u.id AND r.code='CUSTOMER')" +
                    (lock ? " FOR UPDATE OF u" : "")
            )
        ) {
            query.setLong(1, user);
            query.setLong(2, tokenVersion);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Trip là khóa đầu tiên ở mọi mutation booking/progress/cancel; scope/nhà xe lấy từ DB. */
    public Trip trip(Connection connection, long id, boolean lock) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT t.id,t.carrier_id,t.scope_id,t.status,t.departure_at,owner.active AND scope.active AS accepting FROM carrier_trips t JOIN carriers owner ON owner.id=t.carrier_id AND owner.scope_id=t.scope_id JOIN auth_data_scopes scope ON scope.id=t.scope_id WHERE t.id=?" +
                    (lock ? " FOR UPDATE OF t" : "")
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return new Trip(
                    row.getLong(1),
                    row.getLong(2),
                    row.getLong(3),
                    TripStatus.valueOf(row.getString(4)),
                    row.getTimestamp(5).toInstant(),
                    row.getBoolean(6)
                );
            }
        }
    }

    /** Tra cặp điểm trên chính trip; thứ tự/giá/quyền đón-trả chỉ server quyết định. */
    public Stop stop(Connection connection, long tripId, long id) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT id,stop_order,cumulative_fare,pickup_allowed,dropoff_allowed,scheduled_departure_at,actual_departure_at FROM trip_stops WHERE id=? AND trip_id=?"
            )
        ) {
            query.setLong(1, id);
            query.setLong(2, tripId);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.BOOKING_INVALID);
                var actual = row.getTimestamp(7);
                return new Stop(
                    row.getLong(1),
                    row.getInt(2),
                    row.getBigDecimal(3),
                    row.getBoolean(4),
                    row.getBoolean(5),
                    row.getTimestamp(6).toInstant(),
                    actual == null ? null : actual.toInstant()
                );
            }
        }
    }

    /** Minimal parent lookup trước khóa trip, không trả thông tin riêng tư ra caller HTTP. */
    public long parent(Connection connection, long id, Long customerId, PermissionAction action) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT b.trip_id FROM bookings b JOIN carriers owner ON owner.id=b.carrier_id AND owner.scope_id=b.scope_id WHERE b.id=? AND " +
                    (customerId == null ? DataScopeGuard.visibleSql() : "b.customer_id=?")
            )
        ) {
            query.setLong(1, id);
            if (customerId == null) DataScopeGuard.bindVisible(query, 2, Groups.BOOKING, action);
            else query.setLong(2, customerId);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return row.getLong(1);
            }
        }
    }

    public Row find(
        Connection connection,
        long id,
        Long customerId,
        PermissionAction action,
        boolean lock,
        boolean detail
    ) throws SQLException {
        String permission = customerId == null ? DataScopeGuard.visibleSql() : "b.customer_id=?";
        try (
            var query = connection.prepareStatement(
                "SELECT " +
                    COLUMNS +
                    ",(" +
                    (customerId == null ? DataScopeGuard.visibleSql() : "b.customer_id=?") +
                    ") AS can_update" +
                    FROM +
                    " WHERE b.id=? AND " +
                    permission +
                    (lock ? " FOR UPDATE OF b" : "")
            )
        ) {
            int at = bindScope(query, 1, customerId, PermissionAction.UPDATE);
            query.setLong(at++, id);
            bindScope(query, at, customerId, action);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(row, detail ? events(connection, id) : List.of());
            }
        }
    }

    private int bindScope(PreparedStatement query, int at, Long customerId, PermissionAction action)
        throws SQLException {
        if (customerId == null) return DataScopeGuard.bindVisible(query, at, Groups.BOOKING, action);
        query.setLong(at++, customerId);
        return at;
    }

    /** Quyền owner/staff được áp dụng trước COUNT/LIMIT, tìm nguyên văn không dùng wildcard. */
    public PageResponse<BookingResponse> page(Connection connection, BookingSearchRequest search, Long customerId)
        throws SQLException {
        String where =
            FROM +
            " WHERE " +
            (customerId == null ? DataScopeGuard.visibleSql() : "b.customer_id=?") +
            " AND (?::bigint IS NULL OR b.trip_id=?) AND (?::text IS NULL OR b.status=?) AND strpos(lower(concat_ws(' ',b.code,t.code,b.contact_name,b.contact_phone)),?)>0";
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + where)) {
            bindSearch(query, 1, search, customerId);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var result = new ArrayList<BookingResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT " +
                    COLUMNS +
                    ",(" +
                    (customerId == null ? DataScopeGuard.visibleSql() : "b.customer_id=?") +
                    ") AS can_update" +
                    where +
                    " ORDER BY b.id DESC LIMIT ? OFFSET ?"
            )
        ) {
            int at = bindScope(query, 1, customerId, PermissionAction.UPDATE);
            at = bindSearch(query, at, search, customerId);
            query.setInt(at++, search.getSize());
            query.setLong(at, search.offset());
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(map(rows, List.of()).response());
            }
        }
        return PageResponse.of(result, search, total);
    }

    private int bindSearch(PreparedStatement query, int at, BookingSearchRequest search, Long customerId)
        throws SQLException {
        at = bindScope(query, at, customerId, PermissionAction.READ);
        query.setObject(at++, search.getTripId());
        query.setObject(at++, search.getTripId());
        String status = search.getStatus() == null ? null : search.getStatus().name();
        query.setString(at++, status);
        query.setString(at++, status);
        query.setString(at++, search.getQuery() == null ? "" : search.getQuery().strip().toLowerCase(Locale.ROOT));
        return at;
    }

    /** Customer row đã khóa sau trip để tuần tự hóa gửi yêu cầu sang nhiều trip; partial unique là rào cuối. */
    public void requireNoActive(Connection connection, long customerId) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT 1 FROM bookings WHERE customer_id=? AND source_code='WEB' AND status IN('PENDING','CONFIRMED') LIMIT 1"
            )
        ) {
            query.setLong(1, customerId);
            try (var row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.BOOKING_ACTIVE);
            }
        }
    }

    public Long requestId(Connection connection, UUID key) throws SQLException {
        try (var query = connection.prepareStatement("SELECT id FROM bookings WHERE request_key=?")) {
            query.setObject(1, key);
            try (var row = query.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
            }
        }
    }

    /** Danh mục source active được khóa chia sẻ, không tin source_id từ client. */
    public long source(Connection connection, String code) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT id FROM catalog_items WHERE type='BOOKING_SOURCE' AND code=? AND active FOR SHARE"
            )
        ) {
            query.setString(1, code);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.BOOKING_INVALID);
                return row.getLong(1);
            }
        }
    }

    public long insert(
        Connection connection,
        Trip trip,
        Stop pickup,
        Stop dropoff,
        BookingCreateRequest body,
        Long customerId,
        String source,
        long sourceId,
        long actor
    ) throws SQLException {
        BigDecimal price = dropoff.fare().subtract(pickup.fare());
        try (
            var query = connection.prepareStatement(
                "INSERT INTO bookings(trip_id,carrier_id,scope_id,customer_id,source_code,source_id,pickup_stop_id,dropoff_stop_id,pickup_order,dropoff_order,seats,unit_price,total_price,contact_name,contact_phone,request_key,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, trip.id());
            query.setLong(2, trip.carrierId());
            query.setLong(3, trip.scopeId());
            query.setObject(4, customerId);
            query.setString(5, source);
            query.setLong(6, sourceId);
            query.setLong(7, pickup.id());
            query.setLong(8, dropoff.id());
            query.setInt(9, pickup.order());
            query.setInt(10, dropoff.order());
            query.setInt(11, body.seats());
            query.setBigDecimal(12, price);
            query.setBigDecimal(13, price.multiply(BigDecimal.valueOf(body.seats())));
            query.setString(14, body.contactName().strip());
            query.setString(15, body.contactPhone().strip());
            query.setObject(16, body.requestKey());
            query.setLong(17, actor);
            query.setLong(18, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    /** CAS đơn, không nhận trip/scope/source/giá mới; caller kiểm graph và chỗ trước cập nhật. */
    public void state(Connection connection, BookingResponse row, BookingStatus state, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE bookings SET status=?,updated_at=CURRENT_TIMESTAMP,updated_by=?,version=version+1 WHERE id=? AND trip_id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setString(1, state.name());
            query.setLong(2, actor);
            query.setLong(3, row.id());
            query.setLong(4, row.tripId());
            query.setLong(5, row.carrierId());
            query.setLong(6, row.scopeId());
            query.setLong(7, row.version());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Caller đã khóa trip; lifecycle nội bộ lấy đơn active tăng ID và khóa trước segments. */
    public void lockActiveForTrip(Connection connection, long tripId) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT count(*) FROM (SELECT id FROM bookings WHERE trip_id=? AND status IN('PENDING','CONFIRMED') ORDER BY id FOR UPDATE) locked"
            )
        ) {
            query.setLong(1, tripId);
            try (var row = query.executeQuery()) {
                row.next(); // count buộc PostgreSQL khóa hết tập, không tải DTO/PII của mọi đơn lên heap.
            }
        }
    }

    public record PaymentEvent(long bookingId, long actor, String kind, BigDecimal amount, String note) {}

    /** Chỉ trả metadata nội bộ chống lặp, không có projection public hoặc lookup qua request key. */
    public PaymentEvent paymentEvent(Connection connection, UUID key) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT booking_id,created_by,event_type,amount,note FROM booking_events WHERE request_key=?"
            )
        ) {
            query.setObject(1, key);
            try (var row = query.executeQuery()) {
                return row.next()
                    ? new PaymentEvent(
                          row.getLong(1),
                          row.getLong(2),
                          row.getString(3),
                          row.getBigDecimal(4),
                          row.getString(5)
                      )
                    : null;
            }
        }
    }

    /** Ghi nhận tiền thực tế, CAS độc lập trạng thái; không thể vượt tổng giá/đã thu qua CHECK DB. */
    public void money(Connection connection, BookingResponse row, BigDecimal paid, BigDecimal refunded, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE bookings SET paid_amount=?,refunded_amount=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND trip_id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setBigDecimal(1, paid);
            query.setBigDecimal(2, refunded);
            query.setLong(3, actor);
            query.setLong(4, row.id());
            query.setLong(5, row.tripId());
            query.setLong(6, row.carrierId());
            query.setLong(7, row.scopeId());
            query.setLong(8, row.version());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** History là một phần giao dịch; không nuốt lỗi để tránh đổi trạng thái không có lý do. */
    public void event(
        Connection connection,
        BookingResponse row,
        BookingStatus state,
        String type,
        String note,
        BigDecimal amount,
        UUID requestKey,
        long actor
    ) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO booking_events(booking_id,trip_id,carrier_id,scope_id,from_status,to_status,event_type,note,amount,request_key,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
            )
        ) {
            query.setLong(1, row.id());
            query.setLong(2, row.tripId());
            query.setLong(3, row.carrierId());
            query.setLong(4, row.scopeId());
            query.setString(5, type.equals("CREATED") ? null : row.status().name());
            query.setString(6, state.name());
            query.setString(7, type);
            query.setString(8, note);
            query.setBigDecimal(9, amount);
            query.setObject(10, requestKey);
            query.setLong(11, actor);
            query.executeUpdate();
        }
    }

    private List<BookingEventResponse> events(Connection connection, long id) throws SQLException {
        var result = new ArrayList<BookingEventResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT id,event_type,from_status,to_status,amount,note,created_at FROM (SELECT id,event_type,from_status,to_status,amount,note,created_at FROM booking_events WHERE booking_id=? ORDER BY id DESC LIMIT 100) e ORDER BY id"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                while (row.next())
                    result.add(
                        new BookingEventResponse(
                            row.getLong(1),
                            row.getString(2),
                            row.getString(3),
                            row.getString(4),
                            row.getBigDecimal(5),
                            row.getString(6),
                            row.getTimestamp(7).toInstant()
                        )
                    );
            }
        }
        return List.copyOf(result);
    }

    private Row map(ResultSet r, List<BookingEventResponse> events) throws SQLException {
        var status = BookingStatus.valueOf(r.getString("status"));
        BigDecimal paid = r.getBigDecimal("paid_amount"),
            refunded = r.getBigDecimal("refunded_amount");
        var response = new BookingResponse(
            r.getLong("id"),
            r.getString("code"),
            r.getLong("trip_id"),
            r.getString("trip_code"),
            r.getLong("carrier_id"),
            r.getLong("scope_id"),
            r.getString("carrier_name"),
            r.getString("carrier_phone"),
            r.getString("source_code"),
            r.getLong("pickup_stop_id"),
            r.getLong("dropoff_stop_id"),
            r.getInt("pickup_order"),
            r.getInt("dropoff_order"),
            r.getString("pickup_vi"),
            r.getString("pickup_en"),
            r.getString("dropoff_vi"),
            r.getString("dropoff_en"),
            r.getTimestamp("pickup_at").toInstant(),
            r.getTimestamp("dropoff_at").toInstant(),
            r.getInt("seats"),
            r.getBigDecimal("unit_price"),
            r.getBigDecimal("total_price"),
            r.getString("contact_name"),
            r.getString("contact_phone"),
            status,
            paid,
            refunded,
            status == BookingStatus.CANCELLED && paid.compareTo(refunded) > 0,
            r.getLong("version"),
            r.getBoolean("can_update"),
            r.getTimestamp("created_at").toInstant(),
            events
        );
        return new Row(
            response,
            r.getObject("customer_id", Long.class),
            r.getLong("created_by"),
            r.getObject("request_key", UUID.class)
        );
    }
}
