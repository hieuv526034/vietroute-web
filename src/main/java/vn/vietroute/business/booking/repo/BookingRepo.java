package vn.vietroute.business.booking.repo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.booking.dto.response.BookingEventResponse;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.dto.response.DriverPassengerResponse;
import vn.vietroute.business.booking.dto.search.BookingSearch;
import vn.vietroute.business.booking.dto.search.BookingTripSearch;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.validation.BusinessRules;

/** SQL đơn luôn owner hoặc scope trong predicate; trường PII không dùng cho lookup công khai. */
@Repository
public class BookingRepo extends BaseRepository {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Trip {

        private long id;

        private long carrierId;

        private long scopeId;

        private TripStatus status;

        private Instant departureAt;

        private boolean accepting;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Stop {

        private long id;

        private int order;

        private BigDecimal fare;

        private boolean pickup;

        private boolean dropoff;

        private Instant departure;

        private Instant actualDeparture;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Row {

        private BookingResponse response;

        private Long customerId;

        private long createdBy;

        private UUID requestKey;
    }

    private static final String FROM = """
    FROM bookings b
    JOIN carrier_trips t ON t.id = b.trip_id AND t.carrier_id = b.carrier_id
        AND t.scope_id = b.scope_id
    JOIN carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
    JOIN trip_stops pick ON pick.id = b.pickup_stop_id AND pick.trip_id = b.trip_id
    JOIN trip_stops dropoff ON dropoff.id = b.dropoff_stop_id AND dropoff.trip_id = b.trip_id
    """;
    private static final String COLUMNS = """
    b.*, t.code AS trip_code, owner.name AS carrier_name, owner.phone AS carrier_phone, pick.name_vi
        AS pickup_vi, pick.name_en AS pickup_en, dropoff.name_vi AS dropoff_vi, dropoff.name_en AS dropoff_en,
        pick.scheduled_departure_at AS pickup_at, dropoff.scheduled_arrival_at AS dropoff_at
    """;

    private final TripSegmentRepo capacity;

    public BookingRepo(JdbcConnectionFactory connections, TripSegmentRepo capacity) {
        super(connections);
        this.capacity = capacity;
    }

    /** Chỉ gọi sau trips.assigned trong cùng transaction; SQL không trả PENDING/PII không phục vụ chuyến. */
    public PageResponse<DriverPassengerResponse> passengers(Connection connection, long tripId, PageRequest page)
        throws SQLException {
        String filter = """
        %s
        WHERE b.trip_id = ?
            AND b.status IN ('CONFIRMED', 'COMPLETED')
        """.formatted(FROM);
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
            query.setLong(1, tripId);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<DriverPassengerResponse> result = new ArrayList<DriverPassengerResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT b.code, b.pickup_order, b.dropoff_order, pick.name_vi, pick.name_en, dropoff.name_vi,
                    dropoff.name_en, b.seats, b.contact_name, b.contact_phone, b.status
                %s
                ORDER BY b.pickup_order, b.dropoff_order, b.id
                LIMIT ? OFFSET ?
                """.formatted(filter)
            )
        ) {
            query.setLong(1, tripId);
            query.setInt(2, page.getSize());
            query.setLong(3, page.offset());
            try (ResultSet row = query.executeQuery()) {
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

    /** Picker PHONE chỉ hiện trip có CREATE trong scope, không cần grant GET danh sách chuyến riêng và không trả hồ sơ staff. */
    public PageResponse<BookingTripChoiceResponse> choices(Connection connection, BookingTripSearch body)
        throws SQLException {
        String filter = """
        FROM carrier_trips t
        JOIN carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
        WHERE %s AND owner.active
            AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
            AND strpos(lower(concat_ws(' ', t.code, t.name_vi, t.name_en, owner.name)), ?) > 0
        """.formatted(UserRoleRepo.visibleSql());
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
            int at = UserRoleRepo.bindVisible(query, 1);
            query.setString(at, text);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<BookingTripChoiceResponse> result = new ArrayList<BookingTripChoiceResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT t.id, t.code, owner.name, t.name_vi, t.name_en, t.departure_at
                %s
                ORDER BY t.departure_at, t.id
                LIMIT ? OFFSET ?
                """.formatted(filter)
            )
        ) {
            int at = UserRoleRepo.bindVisible(query, 1);
            query.setString(at++, text);
            query.setInt(at++, body.getSize());
            query.setLong(at, body.offset());
            try (ResultSet row = query.executeQuery()) {
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
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT u.id
                FROM auth_users u
                WHERE u.id = ? AND u.token_version = ? AND u.active AND NOT u.activation_pending
                    AND NOT u.must_change_password
                    AND u.email_verified_at IS NOT NULL
                    AND NOT u.deleted
                %s
                """.formatted(lock ? " FOR UPDATE OF u" : "")
            )
        ) {
            query.setLong(1, user);
            query.setLong(2, tokenVersion);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Trip là khóa đầu tiên ở mọi mutation booking/progress/cancel; scope/nhà xe lấy từ DB. */
    public Trip trip(Connection connection, long id, boolean lock) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT t.id, t.carrier_id, t.scope_id, t.status, t.departure_at, owner.active
                    AND scope.active AS accepting
                FROM carrier_trips t
                JOIN carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
                JOIN (
                    SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                        scope_active AS active, scope_version AS version, updated_at, updated_by
                    FROM carriers
                ) scope ON scope.id = t.scope_id
                WHERE t.id = ?
                %s
                """.formatted(lock ? " FOR UPDATE OF t" : "")
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
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
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT id, stop_order, cumulative_fare, pickup_allowed, dropoff_allowed, scheduled_departure_at,
                    actual_departure_at
                FROM trip_stops
                WHERE id = ? AND trip_id = ?
                """
            )
        ) {
            query.setLong(1, id);
            query.setLong(2, tripId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.BOOKING_INVALID);
                Timestamp actual = row.getTimestamp(7);
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
    public long parent(Connection connection, long id, Long customerId) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT b.trip_id
                FROM bookings b
                JOIN carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
                WHERE b.id = ?
                    AND %s
                """.formatted(customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=?")
            )
        ) {
            query.setLong(1, id);
            if (customerId == null) UserRoleRepo.bindVisible(query, 2);
            else query.setLong(2, customerId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return row.getLong(1);
            }
        }
    }

    public Row find(Connection connection, long id, Long customerId, boolean lock, boolean detail) throws SQLException {
        String permission = customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=?";
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s, (%s) AS can_update
                %s
                WHERE b.id = ?
                    AND %s
                %s
                """.formatted(
                    COLUMNS,
                    customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=?",
                    FROM,
                    permission,
                    lock ? " FOR UPDATE OF b" : ""
                )
            )
        ) {
            int at =
                customerId == null
                    ? UserRoleRepo.bindVisible(query, 1, "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE)
                    : bindScope(query, 1, customerId);
            query.setLong(at++, id);
            bindScope(query, at, customerId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(row, detail ? events(connection, id) : List.of());
            }
        }
    }

    private int bindScope(PreparedStatement query, int at, Long customerId) throws SQLException {
        if (customerId == null) return UserRoleRepo.bindVisible(query, at);
        query.setLong(at++, customerId);
        return at;
    }

    /** Quyền owner/staff được áp dụng trước COUNT/LIMIT, tìm nguyên văn không dùng wildcard. */
    public PageResponse<BookingResponse> page(Connection connection, BookingSearch search, Long customerId)
        throws SQLException {
        String where = """
        %s
        WHERE %s
            AND (?::bigint IS NULL OR b.trip_id = ?)
            AND (?::text IS NULL OR b.status = ?)
            AND strpos(lower(concat_ws(' ', b.code, t.code, b.contact_name, b.contact_phone)), ?) > 0
        """.formatted(FROM, customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=?");
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + where)) {
            bindSearch(query, 1, search, customerId);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<BookingResponse> result = new ArrayList<BookingResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s, (%s) AS can_update
                %s
                ORDER BY b.id DESC
                LIMIT ? OFFSET ?
                """.formatted(COLUMNS, customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=?", where)
            )
        ) {
            int at =
                customerId == null
                    ? UserRoleRepo.bindVisible(query, 1, "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE)
                    : bindScope(query, 1, customerId);
            at = bindSearch(query, at, search, customerId);
            query.setInt(at++, search.getSize());
            query.setLong(at, search.offset());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(map(rows, List.of()).getResponse());
            }
        }
        return PageResponse.of(result, search, total);
    }

    private int bindSearch(PreparedStatement query, int at, BookingSearch search, Long customerId) throws SQLException {
        at = bindScope(query, at, customerId);
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
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT 1
                FROM bookings
                WHERE customer_id = ?
                    AND source_code = 'WEB'
                    AND status IN ('PENDING', 'CONFIRMED')
                LIMIT 1
                """
            )
        ) {
            query.setLong(1, customerId);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.BOOKING_ACTIVE);
            }
        }
    }

    public Long requestId(Connection connection, UUID key) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM bookings WHERE request_key=?")) {
            query.setObject(1, key);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
            }
        }
    }

    /** Danh mục source active được khóa chia sẻ, không tin source_id từ client. */
    public long source(Connection connection, String code) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM catalog_items WHERE type='BOOKING_SOURCE' AND code=? AND active FOR SHARE"
            )
        ) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
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
        BookingRequest body,
        Long customerId,
        String source,
        long sourceId,
        long actor
    ) throws SQLException {
        BigDecimal price = dropoff.getFare().subtract(pickup.getFare());
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO bookings(trip_id, carrier_id, scope_id, customer_id, source_code, source_id,
                    pickup_stop_id, dropoff_stop_id, pickup_order, dropoff_order, seats, unit_price, total_price,
                    contact_name, contact_phone, request_key, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """
            )
        ) {
            query.setLong(1, trip.getId());
            query.setLong(2, trip.getCarrierId());
            query.setLong(3, trip.getScopeId());
            query.setObject(4, customerId);
            query.setString(5, source);
            query.setLong(6, sourceId);
            query.setLong(7, pickup.getId());
            query.setLong(8, dropoff.getId());
            query.setInt(9, pickup.getOrder());
            query.setInt(10, dropoff.getOrder());
            query.setInt(11, body.getSeats());
            query.setBigDecimal(12, price);
            query.setBigDecimal(13, price.multiply(BigDecimal.valueOf(body.getSeats())));
            query.setString(14, body.getContactName().strip());
            query.setString(15, body.getContactPhone().strip());
            query.setObject(16, body.getRequestKey());
            query.setLong(17, actor);
            query.setLong(18, actor);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    /** CAS đơn, không nhận trip/scope/source/giá mới; caller kiểm graph và chỗ trước cập nhật. */
    public void state(Connection connection, BookingResponse row, BookingStatus state, long actor) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE bookings
                SET status = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?, version = version + 1
                WHERE id = ? AND trip_id = ? AND carrier_id = ? AND scope_id = ? AND version = ?
                """
            )
        ) {
            query.setString(1, state.name());
            query.setLong(2, actor);
            query.setLong(3, row.getId());
            query.setLong(4, row.getTripId());
            query.setLong(5, row.getCarrierId());
            query.setLong(6, row.getScopeId());
            query.setLong(7, row.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Caller đã khóa trip; lifecycle nội bộ lấy đơn active tăng ID và khóa trước segments. */
    public void lockActiveForTrip(Connection connection, long tripId) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT count(*)
                FROM (
                    SELECT id
                    FROM bookings
                    WHERE trip_id = ?
                        AND status IN ('PENDING', 'CONFIRMED')
                    ORDER BY id
                    FOR UPDATE
                ) locked
                """
            )
        ) {
            query.setLong(1, tripId);
            try (ResultSet row = query.executeQuery()) {
                row.next(); // count buộc PostgreSQL khóa hết tập, không tải DTO/PII của mọi đơn lên heap.
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PaymentEvent {

        private long bookingId;

        private long actor;

        private String kind;

        private BigDecimal amount;

        private String note;
    }

    /** Chỉ trả metadata nội bộ chống lặp, không có projection public hoặc lookup qua request key. */
    public PaymentEvent paymentEvent(Connection connection, UUID key) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT booking_id, created_by, event_type, amount, note
                FROM booking_events
                WHERE request_key = ?
                """
            )
        ) {
            query.setObject(1, key);
            try (ResultSet row = query.executeQuery()) {
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
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE bookings
                SET paid_amount = ?, refunded_amount = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ? AND trip_id = ? AND carrier_id = ? AND scope_id = ? AND version = ?
                """
            )
        ) {
            query.setBigDecimal(1, paid);
            query.setBigDecimal(2, refunded);
            query.setLong(3, actor);
            query.setLong(4, row.getId());
            query.setLong(5, row.getTripId());
            query.setLong(6, row.getCarrierId());
            query.setLong(7, row.getScopeId());
            query.setLong(8, row.getVersion());
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
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO booking_events(booking_id, trip_id, carrier_id, scope_id, from_status, to_status,
                    event_type, note, amount, request_key, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            query.setLong(1, row.getId());
            query.setLong(2, row.getTripId());
            query.setLong(3, row.getCarrierId());
            query.setLong(4, row.getScopeId());
            query.setString(5, type.equals("CREATED") ? null : row.getStatus().name());
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
        ArrayList<BookingEventResponse> result = new ArrayList<BookingEventResponse>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT id, event_type, from_status, to_status, amount, note, created_at
                FROM (
                    SELECT id, event_type, from_status, to_status, amount, note, created_at
                    FROM booking_events
                    WHERE booking_id = ?
                    ORDER BY id DESC
                    LIMIT 100
                ) e
                ORDER BY id
                """
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
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
        BookingStatus status = BookingStatus.valueOf(r.getString("status"));
        BigDecimal paid = r.getBigDecimal("paid_amount"),
            refunded = r.getBigDecimal("refunded_amount");
        BookingResponse response = new BookingResponse(
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

    /** Trip → booking tăng ID → segments tăng dần; cộng chỗ theo đoạn, không query/save theo từng đơn. */
    public void cancelTrip(Connection connection, long tripId, String reason, long actor) throws SQLException {
        this.lockActiveForTrip(connection, tripId);
        capacity.releaseTrip(connection, tripId, actor);
        transition(connection, tripId, 0, BookingStatus.CANCELLED, reason, actor);
    }

    /** Chỉ đóng PENDING đã lỡ điểm đón; tập còn lại và số chỗ không thay đổi. */
    public void departed(Connection connection, long tripId, int order, long actor) throws SQLException {
        transition(
            connection,
            tripId,
            order,
            BookingStatus.REJECTED,
            "Điểm đón đã được tài xế xác nhận rời / Pickup stop departure confirmed",
            actor
        );
    }

    /** Đến điểm trả chỉ hoàn thành CONFIRMED; giữ nguyên số chỗ lịch sử. */
    public void arrived(Connection connection, long tripId, int order, long actor) throws SQLException {
        transition(
            connection,
            tripId,
            order,
            BookingStatus.COMPLETED,
            "Đã đến điểm trả theo xác nhận tài xế / Driver confirmed arrival at drop-off",
            actor
        );
    }

    /** SELECT khóa theo ID, CAS và ghi đủ history trong một statement; mismatch/history lỗi làm caller rollback cả trip/chỗ. */
    private void transition(Connection connection, long tripId, int order, BookingStatus state, String note, long actor)
        throws SQLException {
        String predicate = switch (state) {
            case CANCELLED -> "b.status IN('PENDING','CONFIRMED')";
            case REJECTED -> "b.status='PENDING' AND b.pickup_order<=?";
            case COMPLETED -> "b.status='CONFIRMED' AND b.dropoff_order<=?";
            default -> throw new IllegalArgumentException("Lifecycle không hỗ trợ trạng thái này");
        };
        String sql = """
        WITH parameters AS (
            SELECT ?::text AS state, ?::text AS note, ?::bigint AS actor
        ), targets AS MATERIALIZED (
            SELECT b.id, b.trip_id, b.carrier_id, b.scope_id, b.status, b.version
            FROM bookings b
            WHERE b.trip_id = ?
                AND %s
            ORDER BY b.id
            FOR UPDATE OF b
        ), changed AS (
            UPDATE bookings b
            SET status = p.state, updated_at = CURRENT_TIMESTAMP, updated_by = p.actor, version = b.version + 1
            FROM targets old, parameters p
            WHERE b.id = old.id AND b.trip_id = old.trip_id AND b.carrier_id = old.carrier_id
                AND b.scope_id = old.scope_id
                AND b.version = old.version
            RETURNING b.id, b.trip_id, b.carrier_id, b.scope_id, old.status AS from_status
        ), logged AS (
            INSERT INTO booking_events(booking_id, trip_id, carrier_id, scope_id, from_status, to_status,
                event_type, note, amount, created_by)
            SELECT c.id, c.trip_id, c.carrier_id, c.scope_id, c.from_status, p.state, p.state, p.note, 0,
                p.actor
            FROM changed c
            CROSS JOIN parameters p
            ORDER BY c.id
            RETURNING booking_id
        )
        SELECT (
            SELECT count(*)
            FROM targets
        ), (
            SELECT count(*)
            FROM changed
        ), (
            SELECT count(*)
            FROM logged
        )
        """.formatted(predicate);
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, state.name());
            query.setString(2, note);
            query.setLong(3, actor);
            query.setLong(4, tripId);
            if (state != BookingStatus.CANCELLED) query.setInt(5, order);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                BusinessRules.require(
                    row.getLong(1) == row.getLong(2) && row.getLong(1) == row.getLong(3),
                    ErrorCode.STALE_VERSION
                );
            }
        }
    }
}
