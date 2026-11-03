package vn.vietroute.business.booking.repo;

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
import vn.vietroute.business.trip.repo.TripSegmentRepo;
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
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
    FROM {h-schema}bookings b
    JOIN {h-schema}carrier_trips t ON t.id = b.trip_id AND t.carrier_id = b.carrier_id
        AND t.scope_id = b.scope_id
    JOIN {h-schema}carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
    JOIN {h-schema}trip_stops pick ON pick.id = b.pickup_stop_id AND pick.trip_id = b.trip_id
    JOIN {h-schema}trip_stops dropoff ON dropoff.id = b.dropoff_stop_id AND dropoff.trip_id = b.trip_id
    """;
    private static final String COLUMNS = """
    b.*, t.code AS trip_code, owner.name AS carrier_name, owner.phone AS carrier_phone, pick.name_vi
        AS pickup_vi, pick.name_en AS pickup_en, dropoff.name_vi AS dropoff_vi, dropoff.name_en AS dropoff_en,
        pick.scheduled_departure_at AS pickup_at, dropoff.scheduled_arrival_at AS dropoff_at
    """;

    private final TripSegmentRepo capacity;

    public BookingRepo(EntityManager manager, TripSegmentRepo capacity) {
        super(manager);
        this.capacity = capacity;
    }

    /** Chỉ gọi sau trips.assigned trong cùng transaction; SQL không trả PENDING/PII không phục vụ chuyến. */
    public PageResponse<DriverPassengerResponse> passengers(long tripId, PageRequest page) {
        String filter = """
        %s
        WHERE b.trip_id = :tripId
            AND b.status IN ('CONFIRMED', 'COMPLETED')
        """.formatted(FROM);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            query.setParameter("tripId", tripId);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<DriverPassengerResponse> result = new ArrayList<DriverPassengerResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT b.code, b.pickup_order, b.dropoff_order, pick.name_vi, pick.name_en, dropoff.name_vi,
                    dropoff.name_en, b.seats, b.contact_name, b.contact_phone, b.status
                %s
                ORDER BY b.pickup_order, b.dropoff_order, b.id
                LIMIT :pageSize OFFSET :offset
                """.formatted(filter),
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            query.setParameter("pageSize", page.getSize());
            query.setParameter("offset", page.offset());
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    result.add(
                        new DriverPassengerResponse(
                            stringValue(row.get(0)),
                            intValue(row.get(1)),
                            intValue(row.get(2)),
                            stringValue(row.get(3)),
                            stringValue(row.get(4)),
                            stringValue(row.get(5)),
                            stringValue(row.get(6)),
                            intValue(row.get(7)),
                            stringValue(row.get(8)),
                            stringValue(row.get(9)),
                            stringValue(row.get(10))
                        )
                    );
                }
            }
        }
        return PageResponse.of(result, page, total);
    }

    /** Picker PHONE chỉ hiện trip có CREATE trong scope, không cần grant GET danh sách chuyến riêng và không trả hồ sơ staff. */
    public PageResponse<BookingTripChoiceResponse> choices(BookingTripSearch body) {
        String filter = """
        FROM {h-schema}carrier_trips t
        JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
        WHERE %s AND owner.active
            AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
            AND STRPOS(LOWER(CONCAT_WS(' ', t.code, t.name_vi, t.name_en, owner.name)), :keyword) > 0
        """.formatted(UserRoleRepo.visibleSql());
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            UserRoleRepo.bindVisible(query);
            query.setParameter("keyword", text);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<BookingTripChoiceResponse> result = new ArrayList<BookingTripChoiceResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT t.id, t.code, owner.name, t.name_vi, t.name_en, t.departure_at
                %s
                ORDER BY t.departure_at, t.id
                LIMIT :pageSize OFFSET :offset
                """.formatted(filter),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query);
            query.setParameter("keyword", text);
            query.setParameter("pageSize", body.getSize());
            query.setParameter("offset", body.offset());
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    result.add(
                        new BookingTripChoiceResponse(
                            longValue(row.get(0)),
                            stringValue(row.get(1)),
                            stringValue(row.get(2)),
                            stringValue(row.get(3)),
                            stringValue(row.get(4)),
                            instantValue(row.get(5))
                        )
                    );
                }
            }
        }
        return PageResponse.of(result, body, total);
    }

    /** Không có scope staff thay thế owner; actor đã được kiểm CUSTOMER/ready ở cùng connection. */
    public void requireCustomer(long user, long tokenVersion, boolean lock) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT u.id
                FROM {h-schema}auth_users u
                WHERE u.id = :user AND u.token_version = :tokenVersion AND u.active AND NOT u.activation_pending
                    AND NOT u.must_change_password
                    AND u.email_verified_at IS NOT NULL
                    AND NOT u.deleted
                %s
                """.formatted(lock ? " FOR UPDATE OF u" : ""),
                Tuple.class
            );

            query.setParameter("user", user);
            query.setParameter("tokenVersion", tokenVersion);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                Tuple row = (Tuple) rowValues.get(0);
            }
        }
    }

    /** Trip là khóa đầu tiên ở mọi mutation booking/progress/cancel; scope/nhà xe lấy từ DB. */
    public Trip trip(long id, boolean lock) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT t.id, t.carrier_id, t.scope_id, t.status, t.departure_at, owner.active
                    AND scope.active AS accepting
                FROM {h-schema}carrier_trips t
                JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
                JOIN (
                    SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                        scope_active AS active, scope_version AS version, updated_at, updated_by
                    FROM {h-schema}carriers
                ) scope ON scope.id = t.scope_id
                WHERE t.id = :id
                %s
                """.formatted(lock ? " FOR UPDATE OF t" : ""),
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return new Trip(
                    longValue(row.get(0)),
                    longValue(row.get(1)),
                    longValue(row.get(2)),
                    TripStatus.valueOf(stringValue(row.get(3))),
                    instantValue(row.get(4)),
                    booleanValue(row.get(5))
                );
            }
        }
    }

    /** Tra cặp điểm trên chính trip; thứ tự/giá/quyền đón-trả chỉ server quyết định. */
    public Stop stop(long tripId, long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT id, stop_order, cumulative_fare, pickup_allowed, dropoff_allowed, scheduled_departure_at,
                    actual_departure_at
                FROM {h-schema}trip_stops
                WHERE id = :id AND trip_id = :tripId
                """,
                Tuple.class
            );

            query.setParameter("id", id);
            query.setParameter("tripId", tripId);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.BOOKING_INVALID);
                Tuple row = (Tuple) rowValues.get(0);

                Instant actual = instantValue(row.get(6));
                return new Stop(
                    longValue(row.get(0)),
                    intValue(row.get(1)),
                    decimalValue(row.get(2)),
                    booleanValue(row.get(3)),
                    booleanValue(row.get(4)),
                    instantValue(row.get(5)),
                    actual
                );
            }
        }
    }

    /** Minimal parent lookup trước khóa trip, không trả thông tin riêng tư ra caller HTTP. */
    public long parent(long id, Long customerId) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT b.trip_id
                FROM {h-schema}bookings b
                JOIN {h-schema}carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
                WHERE b.id = :id
                    AND %s
                """.formatted(customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=:customerId"),
                Tuple.class
            );

            query.setParameter("id", id);
            if (customerId == null) UserRoleRepo.bindVisible(query);
            else query.setParameter("customerId", customerId);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return longValue(row.get(0));
            }
        }
    }

    public Row find(long id, Long customerId, boolean lock, boolean detail) {
        String permission = customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=:customerId";
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s, (%s) AS can_update
                %s
                WHERE b.id = :id
                    AND %s
                %s
                """.formatted(
                    COLUMNS,
                    customerId == null ? UserRoleRepo.visibleSql("updateTarget") : "b.customer_id=:customerId",
                    FROM,
                    permission,
                    lock ? " FOR UPDATE OF b" : ""
                ),
                Tuple.class
            );

            if (customerId == null) UserRoleRepo.bindVisible(
                query,
                "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE
            );
            query.setParameter("id", id);
            bindScope(query, customerId);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return map(row, detail ? events(id) : List.of());
            }
        }
    }

    private void bindScope(Query query, Long customerId) {
        if (customerId == null) UserRoleRepo.bindVisible(query);
        else query.setParameter("customerId", customerId);
    }

    /** Quyền owner/staff được áp dụng trước COUNT/LIMIT, tìm nguyên văn không dùng wildcard. */
    public PageResponse<BookingResponse> page(BookingSearch search, Long customerId) {
        String where = """
        %s
        WHERE %s
            AND (CAST(:tripId AS BIGINT) IS NULL OR b.trip_id = :tripId)
            AND (CAST(:status AS TEXT) IS NULL OR b.status = :status)
            AND STRPOS(LOWER(CONCAT_WS(' ', b.code, t.code, b.contact_name, b.contact_phone)), :keyword) > 0
        """.formatted(FROM, customerId == null ? UserRoleRepo.visibleSql() : "b.customer_id=:customerId");
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + where, Tuple.class);

            bindSearch(query, search, customerId);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<BookingResponse> result = new ArrayList<BookingResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s, (%s) AS can_update
                %s
                ORDER BY b.id DESC
                LIMIT :pageSize OFFSET :offset
                """.formatted(
                    COLUMNS,
                    customerId == null ? UserRoleRepo.visibleSql("updateTarget") : "b.customer_id=:customerId",
                    where
                ),
                Tuple.class
            );

            if (customerId == null) UserRoleRepo.bindVisible(
                query,
                "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE
            );
            bindSearch(query, search, customerId);
            query.setParameter("pageSize", search.getSize());
            query.setParameter("offset", search.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(map(rows, List.of()).getResponse());
                }
            }
        }
        return PageResponse.of(result, search, total);
    }

    private void bindSearch(Query query, BookingSearch search, Long customerId) {
        bindScope(query, customerId);
        query.setParameter("tripId", search.getTripId());
        query.setParameter("status", search.getStatus() == null ? null : search.getStatus().name());
        query.setParameter(
            "keyword",
            search.getQuery() == null ? "" : search.getQuery().strip().toLowerCase(Locale.ROOT)
        );
    }

    /** Customer row đã khóa sau trip để tuần tự hóa gửi yêu cầu sang nhiều trip; partial unique là rào cuối. */
    public void requireNoActive(long customerId) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT 1
                FROM {h-schema}bookings
                WHERE customer_id = :customerId
                    AND source_code = 'WEB'
                    AND status IN ('PENDING', 'CONFIRMED')
                LIMIT 1
                """,
                Tuple.class
            );

            query.setParameter("customerId", customerId);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                if (row != null) throw new BusinessException(ErrorCode.BOOKING_ACTIVE);
            }
        }
    }

    public Long requestId(UUID key) {
        {
            Query query = entityManager.createNativeQuery(
                "SELECT id FROM {h-schema}bookings WHERE request_key=:key",
                Tuple.class
            );

            query.setParameter("key", key);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                return row != null ? longValue(row.get(0)) : null;
            }
        }
    }

    /** Danh mục source active được khóa chia sẻ, không tin source_id từ client. */
    public long source(String code) {
        {
            Query query = entityManager.createNativeQuery(
                "SELECT id FROM {h-schema}category WHERE ref_type='BOOKING_SOURCE' AND type='VALUE' AND code=:code AND active FOR SHARE",
                Tuple.class
            );

            query.setParameter("code", code);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.BOOKING_INVALID);
                Tuple row = (Tuple) rowValues.get(0);

                return longValue(row.get(0));
            }
        }
    }

    public long insert(
        Trip trip,
        Stop pickup,
        Stop dropoff,
        BookingRequest body,
        Long customerId,
        String source,
        long sourceId,
        long actor
    ) {
        BigDecimal price = dropoff.getFare().subtract(pickup.getFare());
        {
            Query query = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}bookings(trip_id, carrier_id, scope_id, customer_id, source_code, source_id,
                    pickup_stop_id, dropoff_stop_id, pickup_order, dropoff_order, seats, unit_price, total_price,
                    contact_name, contact_phone, request_key, created_by, updated_by)
                VALUES (:tripId, :carrierId, :scopeId, :customerId, :source, :sourceId, :pickupId, :dropoffId, :order, :order10, :seats, :price, :seats13, :contactName, :contactPhone, :requestKey, :actor, :actor)
                RETURNING id
                """,
                Tuple.class
            );

            query.setParameter("tripId", trip.getId());
            query.setParameter("carrierId", trip.getCarrierId());
            query.setParameter("scopeId", trip.getScopeId());
            query.setParameter("customerId", customerId);
            query.setParameter("source", source);
            query.setParameter("sourceId", sourceId);
            query.setParameter("pickupId", pickup.getId());
            query.setParameter("dropoffId", dropoff.getId());
            query.setParameter("order", pickup.getOrder());
            query.setParameter("order10", dropoff.getOrder());
            query.setParameter("seats", body.getSeats());
            query.setParameter("price", price);
            query.setParameter("seats13", price.multiply(BigDecimal.valueOf(body.getSeats())));
            query.setParameter("contactName", body.getContactName().strip());
            query.setParameter("contactPhone", body.getContactPhone().strip());
            query.setParameter("requestKey", body.getRequestKey());
            query.setParameter("actor", actor);
            {
                Tuple row = (Tuple) query.getSingleResult();
                return longValue(row.get(0));
            }
        }
    }

    /** CAS đơn, không nhận trip/scope/source/giá mới; caller kiểm graph và chỗ trước cập nhật. */
    public void state(BookingResponse row, BookingStatus state, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}bookings
                SET status = :state, updated_at = CURRENT_TIMESTAMP, updated_by = :actor, version = version + 1
                WHERE id = :rowId AND trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :rowVersion
                """
            );

            query.setParameter("state", state.name());
            query.setParameter("actor", actor);
            query.setParameter("rowId", row.getId());
            query.setParameter("tripId", row.getTripId());
            query.setParameter("carrierId", row.getCarrierId());
            query.setParameter("scopeId", row.getScopeId());
            query.setParameter("rowVersion", row.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Caller đã khóa trip; lifecycle nội bộ lấy đơn active tăng ID và khóa trước segments. */
    public void lockActiveForTrip(long tripId) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT id
                    FROM {h-schema}bookings
                    WHERE trip_id = :tripId
                        AND status IN ('PENDING', 'CONFIRMED')
                    ORDER BY id
                    FOR UPDATE
                ) LOCKED
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            {
                Tuple row = (Tuple) query.getSingleResult(); // count buộc PostgreSQL khóa hết tập, không tải DTO/PII của mọi đơn lên heap.
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
    public PaymentEvent paymentEvent(UUID key) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT booking_id, created_by, event_type, amount, note
                FROM {h-schema}booking_events
                WHERE request_key = :key
                """,
                Tuple.class
            );

            query.setParameter("key", key);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                return row != null
                    ? new PaymentEvent(
                          longValue(row.get(0)),
                          longValue(row.get(1)),
                          stringValue(row.get(2)),
                          decimalValue(row.get(3)),
                          stringValue(row.get(4))
                      )
                    : null;
            }
        }
    }

    /** Ghi nhận tiền thực tế, CAS độc lập trạng thái; không thể vượt tổng giá/đã thu qua CHECK DB. */
    public void money(BookingResponse row, BigDecimal paid, BigDecimal refunded, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}bookings
                SET paid_amount = :paid, refunded_amount = :refunded, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :rowId AND trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :rowVersion
                """
            );

            query.setParameter("paid", paid);
            query.setParameter("refunded", refunded);
            query.setParameter("actor", actor);
            query.setParameter("rowId", row.getId());
            query.setParameter("tripId", row.getTripId());
            query.setParameter("carrierId", row.getCarrierId());
            query.setParameter("scopeId", row.getScopeId());
            query.setParameter("rowVersion", row.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** History là một phần giao dịch; không nuốt lỗi để tránh đổi trạng thái không có lý do. */
    public void event(
        BookingResponse row,
        BookingStatus state,
        String type,
        String note,
        BigDecimal amount,
        UUID requestKey,
        long actor
    ) {
        {
            Query query = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}booking_events(booking_id, trip_id, carrier_id, scope_id, from_status, to_status,
                    event_type, note, amount, request_key, created_by)
                VALUES (:rowId, :tripId, :carrierId, :scopeId, :status, :state, :type, :note, :amount, :requestKey, :actor)
                """
            );

            query.setParameter("rowId", row.getId());
            query.setParameter("tripId", row.getTripId());
            query.setParameter("carrierId", row.getCarrierId());
            query.setParameter("scopeId", row.getScopeId());
            query.setParameter("status", type.equals("CREATED") ? null : row.getStatus().name());
            query.setParameter("state", state.name());
            query.setParameter("type", type);
            query.setParameter("note", note);
            query.setParameter("amount", amount);
            query.setParameter("requestKey", requestKey);
            query.setParameter("actor", actor);
            query.executeUpdate();
        }
    }

    private List<BookingEventResponse> events(long id) {
        ArrayList<BookingEventResponse> result = new ArrayList<BookingEventResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT id, event_type, from_status, to_status, amount, note, created_at
                FROM (
                    SELECT id, event_type, from_status, to_status, amount, note, created_at
                    FROM {h-schema}booking_events
                    WHERE booking_id = :id
                    ORDER BY id DESC
                    LIMIT 100
                ) e
                ORDER BY id
                """,
                Tuple.class
            );

            query.setParameter("id", id);
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    result.add(
                        new BookingEventResponse(
                            longValue(row.get(0)),
                            stringValue(row.get(1)),
                            stringValue(row.get(2)),
                            stringValue(row.get(3)),
                            decimalValue(row.get(4)),
                            stringValue(row.get(5)),
                            instantValue(row.get(6))
                        )
                    );
                }
            }
        }
        return List.copyOf(result);
    }

    private Row map(Tuple r, List<BookingEventResponse> events) {
        BookingStatus status = BookingStatus.valueOf(stringValue(r.get("status")));
        BigDecimal paid = decimalValue(r.get("paid_amount")),
            refunded = decimalValue(r.get("refunded_amount"));
        BookingResponse response = new BookingResponse(
            longValue(r.get("id")),
            stringValue(r.get("code")),
            longValue(r.get("trip_id")),
            stringValue(r.get("trip_code")),
            longValue(r.get("carrier_id")),
            longValue(r.get("scope_id")),
            stringValue(r.get("carrier_name")),
            stringValue(r.get("carrier_phone")),
            stringValue(r.get("source_code")),
            longValue(r.get("pickup_stop_id")),
            longValue(r.get("dropoff_stop_id")),
            intValue(r.get("pickup_order")),
            intValue(r.get("dropoff_order")),
            stringValue(r.get("pickup_vi")),
            stringValue(r.get("pickup_en")),
            stringValue(r.get("dropoff_vi")),
            stringValue(r.get("dropoff_en")),
            instantValue(r.get("pickup_at")),
            instantValue(r.get("dropoff_at")),
            intValue(r.get("seats")),
            decimalValue(r.get("unit_price")),
            decimalValue(r.get("total_price")),
            stringValue(r.get("contact_name")),
            stringValue(r.get("contact_phone")),
            status,
            paid,
            refunded,
            status == BookingStatus.CANCELLED && paid.compareTo(refunded) > 0,
            longValue(r.get("version")),
            booleanValue(r.get("can_update")),
            instantValue(r.get("created_at")),
            events
        );
        return new Row(
            response,
            r.get("customer_id", Long.class),
            longValue(r.get("created_by")),
            r.get("request_key", UUID.class)
        );
    }

    /** Trip → booking tăng ID → segments tăng dần; cộng chỗ theo đoạn, không query/save theo từng đơn. */
    public void cancelTrip(Connection connection, long tripId, String reason, long actor) throws SQLException {
        this.lockActiveForTrip(tripId);
        capacity.releaseTrip(connection, tripId, actor);
        transition(tripId, 0, BookingStatus.CANCELLED, reason, actor);
    }

    /** Chỉ đóng PENDING đã lỡ điểm đón; tập còn lại và số chỗ không thay đổi. */
    public void departed(long tripId, int order, long actor) {
        transition(
            tripId,
            order,
            BookingStatus.REJECTED,
            "Điểm đón đã được tài xế xác nhận rời / Pickup stop departure confirmed",
            actor
        );
    }

    /** Đến điểm trả chỉ hoàn thành CONFIRMED; giữ nguyên số chỗ lịch sử. */
    public void arrived(long tripId, int order, long actor) {
        transition(
            tripId,
            order,
            BookingStatus.COMPLETED,
            "Đã đến điểm trả theo xác nhận tài xế / Driver confirmed arrival at drop-off",
            actor
        );
    }

    /** SELECT khóa theo ID, CAS và ghi đủ history trong một statement; mismatch/history lỗi làm caller rollback cả trip/chỗ. */
    private void transition(long tripId, int order, BookingStatus state, String note, long actor) {
        String predicate = switch (state) {
            case CANCELLED -> "b.status IN('PENDING','CONFIRMED')";
            case REJECTED -> "b.status='PENDING' AND b.pickup_order<=:stopOrder";
            case COMPLETED -> "b.status='CONFIRMED' AND b.dropoff_order<=:stopOrder";
            default -> throw new IllegalArgumentException("Lifecycle không hỗ trợ trạng thái này");
        };
        String sql = """
        WITH parameters AS (
            SELECT CAST(:state AS TEXT) AS state, CAST(:note AS TEXT) AS note, CAST(:actor AS BIGINT) AS actor
        ), targets AS MATERIALIZED (
            SELECT b.id, b.trip_id, b.carrier_id, b.scope_id, b.status, b.version
            FROM {h-schema}bookings b
            WHERE b.trip_id = :tripId
                AND %s
            ORDER BY b.id
            FOR UPDATE OF b
        ), changed AS (
            UPDATE {h-schema}bookings b
            SET status = p.state, updated_at = CURRENT_TIMESTAMP, updated_by = p.actor, version = b.version + 1
            FROM targets old, parameters p
            WHERE b.id = old.id AND b.trip_id = old.trip_id AND b.carrier_id = old.carrier_id
                AND b.scope_id = old.scope_id
                AND b.version = old.version
            RETURNING b.id, b.trip_id, b.carrier_id, b.scope_id, old.status AS from_status
        ), logged AS (
            INSERT INTO {h-schema}booking_events(booking_id, trip_id, carrier_id, scope_id, from_status, to_status,
                event_type, note, amount, created_by)
            SELECT c.id, c.trip_id, c.carrier_id, c.scope_id, c.from_status, p.state, p.state, p.note, 0,
                p.actor
            FROM changed c
            CROSS JOIN parameters p
            ORDER BY c.id
            RETURNING booking_id
        )
        SELECT (
            SELECT COUNT(*)
            FROM targets
        ), (
            SELECT COUNT(*)
            FROM changed
        ), (
            SELECT COUNT(*)
            FROM logged
        )
        """.formatted(predicate);
        {
            Query query = entityManager.createNativeQuery(sql, Tuple.class);

            query.setParameter("state", state.name());
            query.setParameter("note", note);
            query.setParameter("actor", actor);
            query.setParameter("tripId", tripId);
            if (state != BookingStatus.CANCELLED) query.setParameter("stopOrder", order);
            {
                Tuple row = (Tuple) query.getSingleResult();
                BusinessRules.require(
                    longValue(row.get(0)) == longValue(row.get(1)) && longValue(row.get(0)) == longValue(row.get(2)),
                    ErrorCode.STALE_VERSION
                );
            }
        }
    }
}
