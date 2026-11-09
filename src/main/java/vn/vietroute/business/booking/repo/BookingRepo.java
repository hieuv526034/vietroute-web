package vn.vietroute.business.booking.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.decimalValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.booking.dto.projection.BookingEventProjection;
import vn.vietroute.business.booking.dto.projection.BookingPassengerProjection;
import vn.vietroute.business.booking.dto.projection.BookingTransitionProjection;
import vn.vietroute.business.booking.dto.projection.BookingTripProjection;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.booking.dto.response.BookingEventResponse;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.dto.response.DriverPassengerResponse;
import vn.vietroute.business.booking.dto.search.BookingSearch;
import vn.vietroute.business.booking.dto.search.BookingTripSearch;
import vn.vietroute.business.booking.entity.Booking;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.projection.TripStopProjection;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của BookingRepo; transaction ghi thuộc Service.
 */
public interface BookingRepo extends Repository<Booking, Long> {
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

    String FROM = """
    FROM {h-schema}bookings b
    JOIN {h-schema}carrier_trips t ON t.id = b.trip_id AND t.carrier_id = b.carrier_id
        AND t.scope_id = b.scope_id
    JOIN {h-schema}carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
    JOIN {h-schema}trip_stops pick ON pick.id = b.pickup_stop_id AND pick.trip_id = b.trip_id
    JOIN {h-schema}trip_stops dropoff ON dropoff.id = b.dropoff_stop_id AND dropoff.trip_id = b.trip_id
    """;

    String COLUMNS = """
    b.*, t.code AS trip_code, owner.name AS carrier_name, owner.phone AS carrier_phone, pick.name_vi
    AS pickup_vi, pick.name_en AS pickup_en, dropoff.name_vi AS dropoff_vi, dropoff.name_en AS dropoff_en,
    pick.scheduled_departure_at AS pickup_at, dropoff.scheduled_arrival_at AS dropoff_at
    """;

    String CONTACT_ALLOWED = """
    b.customer_id = :customerId AND b.status = 'CONFIRMED' AND t.status IN ('OPEN', 'IN_PROGRESS')
    """;

    String CONTACT_COLUMNS =
        ", CASE WHEN " +
        CONTACT_ALLOWED +
        " THEN t.driver_name END AS driver_name, CASE WHEN " +
        CONTACT_ALLOWED +
        " THEN t.driver_phone END AS driver_phone, CASE WHEN " +
        CONTACT_ALLOWED +
        " THEN t.plate_number END AS plate_number ";

    String PERMISSION =
        "((CAST(:customerId AS BIGINT) IS NULL AND " +
        UserRoleRepo.VISIBLE +
        ") OR (CAST(:customerId AS BIGINT) IS NOT NULL AND b.customer_id = :customerId)) ";

    String CAN_UPDATE =
        "CASE WHEN CAST(:customerId AS BIGINT) IS NULL THEN ( " +
        UserRoleRepo.UPDATE_VISIBLE +
        ") ELSE b.customer_id = :customerId END ";

    String PASSENGERS_FILTER = FROM + "WHERE b.trip_id = :tripId AND b.status IN ( 'CONFIRMED' , 'COMPLETED' ) ";

    @Query(value = "SELECT COUNT(*) " + PASSENGERS_FILTER, nativeQuery = true)
    long passengersCount(long tripId);

    @Query(
        value = """
        SELECT b.code AS "bookingCode", b.pickup_order AS "pickupOrder", b.dropoff_order AS "dropoffOrder",
            pick.name_vi AS "pickupNameVi", pick.name_en AS "pickupNameEn",
            dropoff.name_vi AS "dropoffNameVi", dropoff.name_en AS "dropoffNameEn", b.seats,
            b.contact_name AS "contactName", b.contact_phone AS "contactPhone", b.status
        """ +
            PASSENGERS_FILTER +
            "ORDER BY b.pickup_order, b.dropoff_order, b.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<BookingPassengerProjection> passengersRows(long tripId, int pageSize, long offset);

    @Transactional(readOnly = true)
    default PageResponse<DriverPassengerResponse> passengers(long tripId, PageRequest body) {
        long total = passengersCount(tripId);
        List<DriverPassengerResponse> result = passengersRows(tripId, body.getSize(), body.offset())
            .stream()
            .map(row -> {
                return new DriverPassengerResponse(
                    row.getBookingCode(),
                    row.getPickupOrder(),
                    row.getDropoffOrder(),
                    row.getPickupNameVi(),
                    row.getPickupNameEn(),
                    row.getDropoffNameVi(),
                    row.getDropoffNameEn(),
                    row.getSeats(),
                    row.getContactName(),
                    row.getContactPhone(),
                    row.getStatus()
                );
            })
            .toList();
        return PageResponse.of(result, body, total);
    }

    String CHOICES_FILTER =
        """
        FROM {h-schema}carrier_trips t
        JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
        AND owner.active
        AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
        AND STRPOS(LOWER(CONCAT_WS(' ', t.code, t.name_vi, t.name_en, owner.name)), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + CHOICES_FILTER, nativeQuery = true)
    long choicesCount(long viewerId, long viewerVersion, String viewTarget, String keyword);

    @Query(
        value = """
        SELECT t.id, t.code, owner.name AS "carrierName", t.name_vi AS "nameVi",
            t.name_en AS "nameEn", t.departure_at AS "departureAt"
        """ +
            CHOICES_FILTER +
            "ORDER BY t.departure_at, t.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<BookingTripProjection> choicesRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String keyword,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<BookingTripChoiceResponse> choices(BookingTripSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        String keyword = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total = choicesCount(actor.id(), actor.tokenVersion(), actor.getTarget(), keyword);
        List<BookingTripChoiceResponse> result = choicesRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            keyword,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(row -> {
                return new BookingTripChoiceResponse(
                    row.getId(),
                    row.getCode(),
                    row.getCarrierName(),
                    row.getNameVi(),
                    row.getNameEn(),
                    row.getDepartureAt()
                );
            })
            .toList();
        return PageResponse.of(result, body, total);
    }

    @Query(
        value = """
        SELECT u.id
        FROM {h-schema}auth_users u
        WHERE u.id = :user AND u.token_version = :tokenVersion AND u.active AND NOT u.activation_pending
            AND NOT u.must_change_password
            AND NOT u.deleted
        """,
        nativeQuery = true
    )
    List<Long> customerRows(long user, long tokenVersion);

    @Query(
        value = """
        SELECT u.id
        FROM {h-schema}auth_users u
        WHERE u.id = :user AND u.token_version = :tokenVersion AND u.active AND NOT u.activation_pending
            AND NOT u.must_change_password
            AND NOT u.deleted
        FOR UPDATE OF u
        """,
        nativeQuery = true
    )
    List<Long> customerLockedRows(long user, long tokenVersion);

    @Transactional(propagation = Propagation.MANDATORY)
    default void requireCustomer(long user, long version, boolean lock) {
        BusinessRules.require(
            !(lock ? customerLockedRows(user, version) : customerRows(user, version)).isEmpty(),
            ErrorCode.ACCESS_DENIED
        );
    }

    @Query(
        value = """
        SELECT t.id, t.carrier_id AS "carrierId", t.scope_id AS "scopeId", t.status,
            t.departure_at AS "departureAt", owner.active AND scope.active AS "accepting"
        FROM {h-schema}carrier_trips t
        JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
        JOIN (
            SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                scope_active AS active, scope_version AS version, updated_at, updated_by
            FROM {h-schema}carriers
        ) scope ON scope.id = t.scope_id
        WHERE t.id = :id
        """,
        nativeQuery = true
    )
    List<BookingTripProjection> tripRows(long id);

    @Query(
        value = """
        SELECT t.id, t.carrier_id AS "carrierId", t.scope_id AS "scopeId", t.status,
            t.departure_at AS "departureAt", owner.active AND scope.active AS "accepting"
        FROM {h-schema}carrier_trips t
        JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
        JOIN (
            SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                scope_active AS active, scope_version AS version, updated_at, updated_by
            FROM {h-schema}carriers
        ) scope ON scope.id = t.scope_id
        WHERE t.id = :id
        FOR UPDATE OF t
        """,
        nativeQuery = true
    )
    List<BookingTripProjection> tripLockedRows(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default Trip trip(long id, boolean lock) {
        List<BookingTripProjection> rows = lock ? tripLockedRows(id) : tripRows(id);
        BusinessRules.require(!rows.isEmpty(), ErrorCode.NOT_FOUND);
        BookingTripProjection row = rows.get(0);
        return new Trip(
            row.getId(),
            row.getCarrierId(),
            row.getScopeId(),
            TripStatus.valueOf(row.getStatus()),
            row.getDepartureAt(),
            row.getAccepting()
        );
    }

    @Query(
        value = """
        SELECT id, stop_order AS "stopOrder", cumulative_fare AS "cumulativeFare",
            pickup_allowed AS "pickupAllowed", dropoff_allowed AS "dropoffAllowed",
            scheduled_departure_at AS "scheduledDepartureAt", actual_departure_at AS "actualDepartureAt"
        FROM {h-schema}trip_stops
        WHERE id = :id AND trip_id = :tripId
        """,
        nativeQuery = true
    )
    List<TripStopProjection> stopRows(long tripId, long id);

    default Stop stop(long tripId, long id) {
        List<TripStopProjection> rows = stopRows(tripId, id);
        BusinessRules.require(!rows.isEmpty(), ErrorCode.BOOKING_INVALID);
        TripStopProjection row = rows.get(0);
        return new Stop(
            row.getId(),
            row.getStopOrder(),
            row.getCumulativeFare(),
            row.getPickupAllowed(),
            row.getDropoffAllowed(),
            row.getScheduledDepartureAt(),
            row.getActualDepartureAt()
        );
    }

    @Query(
        value = """
        SELECT b.trip_id
        FROM {h-schema}bookings b
        JOIN {h-schema}carriers owner ON owner.id = b.carrier_id AND owner.scope_id = b.scope_id
        WHERE b.id = :id
            AND
        """ + PERMISSION,
        nativeQuery = true
    )
    Long parentRow(long viewerId, long viewerVersion, String viewTarget, Long customerId, long id);

    default long parent(long id, Long customerId) {
        UserPrincipal actor = UserPrincipal.current();
        Long trip = parentRow(actor.id(), actor.tokenVersion(), actor.getTarget(), customerId, id);
        return BusinessRules.requireFound(trip);
    }

    @Query(
        value = "SELECT " +
            COLUMNS +
            CONTACT_COLUMNS +
            ", ( " +
            CAN_UPDATE +
            ") AS can_update " +
            FROM +
            "WHERE b.id = :id AND " +
            PERMISSION,
        nativeQuery = true
    )
    Map<String, Object> detailRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        Long customerId,
        long id
    );

    @Query(
        value = "SELECT " +
            COLUMNS +
            ", ( " +
            CAN_UPDATE +
            ") AS can_update " +
            FROM +
            "WHERE b.id = :id AND " +
            PERMISSION +
            "FOR UPDATE OF b ",
        nativeQuery = true
    )
    Map<String, Object> lockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        Long customerId,
        long id
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default Row find(long id, Long customerId, boolean lock, boolean detail) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE;
        Map<String, Object> row = lock
            ? lockedRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, customerId, id)
            : detailRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, customerId, id);
        if (row == null || row.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return map(row, detail ? events(id) : List.of());
    }

    String FILTER =
        FROM +
        "WHERE " +
        PERMISSION +
        """
        AND (CAST(:tripId AS BIGINT) IS NULL OR b.trip_id = :tripId)
        AND (CAST(:status AS TEXT) IS NULL OR b.status = :status)
        AND STRPOS(LOWER(CONCAT_WS(' ', b.code, t.code, b.contact_name, b.contact_phone)), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + FILTER, nativeQuery = true)
    long searchCount(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long customerId,
        Long tripId,
        String status,
        String keyword
    );

    @Query(
        value = "SELECT " +
            COLUMNS +
            ", ( " +
            CAN_UPDATE +
            ") AS can_update " +
            FILTER +
            "ORDER BY b.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> searchRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long customerId,
        Long tripId,
        String status,
        String keyword,
        String updateTarget,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<BookingResponse> page(BookingSearch body, Long customerId) {
        UserPrincipal actor = UserPrincipal.current();
        String status = body.getStatus() == null ? null : body.getStatus().name();
        String keyword = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total = searchCount(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            customerId,
            body.getTripId(),
            status,
            keyword
        );
        List<BookingResponse> rows = searchRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            customerId,
            body.getTripId(),
            status,
            keyword,
            "PUT " + Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(row -> map(row, List.of()).getResponse())
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1 FROM {h-schema}bookings
            WHERE customer_id = :customerId AND source_code = 'WEB' AND status IN ('PENDING', 'CONFIRMED')
        )
        """,
        nativeQuery = true
    )
    boolean hasActive(long customerId);

    default void requireNoActive(long id) {
        BusinessRules.require(!hasActive(id), ErrorCode.BOOKING_ACTIVE);
    }

    @Query(value = "SELECT id FROM {h-schema}bookings WHERE request_key = :key ", nativeQuery = true)
    Long requestId(UUID key);

    @Query(
        value = """
        SELECT id
        FROM {h-schema}category
        WHERE ref_type = 'BOOKING_SOURCE' AND type = 'VALUE' AND code = :code AND active
        FOR SHARE
        """,
        nativeQuery = true
    )
    Long sourceRow(String code);

    @Transactional(propagation = Propagation.MANDATORY)
    default long source(String code) {
        Long id = sourceRow(code);
        BusinessRules.require(id != null, ErrorCode.BOOKING_INVALID);
        return id;
    }

    @Query(
        value = """
        INSERT INTO {h-schema}bookings(trip_id, carrier_id, scope_id, customer_id, source_code, source_id,
            pickup_stop_id, dropoff_stop_id, pickup_order, dropoff_order, seats, unit_price, total_price,
            contact_name, contact_phone, request_key, created_by, updated_by)
        VALUES (:tripId, :carrierId, :scopeId, :customerId, :source, :sourceId, :pickupId, :dropoffId, :pickupOrder,
                :dropoffOrder, :seats, :price, :total, :contactName, :contactPhone, :requestKey, :actor, :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertRow(
        long tripId,
        long carrierId,
        long scopeId,
        Long customerId,
        String source,
        long sourceId,
        long pickupId,
        long dropoffId,
        int pickupOrder,
        int dropoffOrder,
        int seats,
        BigDecimal price,
        BigDecimal total,
        String contactName,
        String contactPhone,
        UUID requestKey,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insert(
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
        return insertRow(
            trip.getId(),
            trip.getCarrierId(),
            trip.getScopeId(),
            customerId,
            source,
            sourceId,
            pickup.getId(),
            dropoff.getId(),
            pickup.getOrder(),
            dropoff.getOrder(),
            body.getSeats(),
            price,
            price.multiply(BigDecimal.valueOf(body.getSeats())),
            body.getContactName().strip(),
            body.getContactPhone().strip(),
            body.getRequestKey(),
            actor
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}bookings
        SET status = :state, updated_at = CURRENT_TIMESTAMP, updated_by = :actor, version = version + 1
        WHERE id = :rowId AND trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version =
            :rowVersion
        """,
        nativeQuery = true
    )
    int stateRow(String state, long actor, long rowId, long tripId, long carrierId, long scopeId, long rowVersion);

    @Transactional(propagation = Propagation.MANDATORY)
    default void state(BookingResponse row, BookingStatus state, long actor) {
        BusinessRules.require(
            stateRow(
                state.name(),
                actor,
                row.getId(),
                row.getTripId(),
                row.getCarrierId(),
                row.getScopeId(),
                row.getVersion()
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}bookings
        SET paid_amount = :paid, refunded_amount = :refunded, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = :rowId AND trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version =
            :rowVersion
            AND :paid BETWEEN paid_amount AND total_price
            AND :refunded BETWEEN refunded_amount AND :paid
        """,
        nativeQuery = true
    )
    int moneyRow(
        BigDecimal paid,
        BigDecimal refunded,
        long actor,
        long rowId,
        long tripId,
        long carrierId,
        long scopeId,
        long rowVersion
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void money(BookingResponse row, BigDecimal paid, BigDecimal refunded, long actor) {
        BusinessRules.require(
            moneyRow(
                paid,
                refunded,
                actor,
                row.getId(),
                row.getTripId(),
                row.getCarrierId(),
                row.getScopeId(),
                row.getVersion()
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
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
        nativeQuery = true
    )
    long lockActiveForTrip(long tripId);

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

    @Query(
        value = """
        SELECT booking_id AS "bookingId", created_by AS "actor", event_type AS "eventType", amount, note
        FROM {h-schema}booking_events
        WHERE request_key = :key
        """,
        nativeQuery = true
    )
    List<BookingEventProjection> paymentRows(UUID key);

    default PaymentEvent paymentEvent(UUID key) {
        List<BookingEventProjection> rows = paymentRows(key);
        if (rows.isEmpty()) return null;
        BookingEventProjection row = rows.get(0);
        return new PaymentEvent(row.getBookingId(), row.getActor(), row.getEventType(), row.getAmount(), row.getNote());
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO {h-schema}booking_events(booking_id, trip_id, carrier_id, scope_id, from_status, to_status,
            event_type, note, amount, request_key, created_by)
        VALUES (:rowId, :tripId, :carrierId, :scopeId, :status, :state, :type, :note, :amount, :requestKey, :actor)
        """,
        nativeQuery = true
    )
    int eventRow(
        long rowId,
        long tripId,
        long carrierId,
        long scopeId,
        String status,
        String state,
        String type,
        String note,
        BigDecimal amount,
        UUID requestKey,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void event(
        BookingResponse row,
        BookingStatus state,
        String type,
        String note,
        BigDecimal amount,
        UUID requestKey,
        long actor
    ) {
        BusinessRules.require(
            eventRow(
                row.getId(),
                row.getTripId(),
                row.getCarrierId(),
                row.getScopeId(),
                type.equals("CREATED") ? null : row.getStatus().name(),
                state.name(),
                type,
                note,
                amount,
                requestKey,
                actor
            ) == 1,
            ErrorCode.DATABASE_UNAVAILABLE
        );
    }

    @Query(
        value = """
        SELECT id, event_type AS "eventType", from_status AS "fromStatus", to_status AS "toStatus",
            amount, note, created_at AS "createdAt"
        FROM (
            SELECT id, event_type, from_status, to_status, amount, note, created_at
            FROM {h-schema}booking_events
            WHERE booking_id = :id
            ORDER BY id DESC
            LIMIT 100
        ) e
        ORDER BY id
        """,
        nativeQuery = true
    )
    List<BookingEventProjection> eventRows(long id);

    private List<BookingEventResponse> events(long id) {
        return eventRows(id)
            .stream()
            .map(row ->
                new BookingEventResponse(
                    row.getId(),
                    row.getEventType(),
                    row.getFromStatus(),
                    row.getToStatus(),
                    row.getAmount(),
                    row.getNote(),
                    row.getCreatedAt()
                )
            )
            .toList();
    }

    private static Row map(Map<String, Object> r, List<BookingEventResponse> events) {
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
            events,
            stringValue(r.get("driver_name")),
            stringValue(r.get("driver_phone")),
            stringValue(r.get("plate_number"))
        );
        return new Row(
            response,
            (Long) r.get("customer_id"),
            longValue(r.get("created_by")),
            (UUID) r.get("request_key")
        );
    }

    @Query(
        value = """
        WITH parameters AS (
            SELECT CAST(:state AS TEXT) AS state, CAST(:note AS TEXT) AS note, CAST(:actor AS BIGINT) AS actor
        ), targets AS MATERIALIZED (
            SELECT b.id, b.trip_id, b.carrier_id, b.scope_id, b.status, b.version
            FROM {h-schema}bookings b
            WHERE b.trip_id = :tripId
                AND ((:state = 'CANCELLED' AND b.status IN ('PENDING', 'CONFIRMED'))
                    OR (:state = 'REJECTED' AND b.status = 'PENDING' AND b.pickup_order <= :stopOrder)
                    OR (:state = 'COMPLETED' AND b.status = 'CONFIRMED' AND b.dropoff_order <= :stopOrder))
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
            SELECT c.id, c.trip_id, c.carrier_id, c.scope_id, c.from_status, p.state, p.state, p.note, 0, p.actor
            FROM changed c
            CROSS JOIN parameters p
            ORDER BY c.id
            RETURNING booking_id
        )
        SELECT (SELECT COUNT(*) FROM targets) AS "targetCount", (SELECT COUNT(*) FROM changed) AS "changedCount",
            (SELECT COUNT(*) FROM logged) AS "loggedCount"
        """,
        nativeQuery = true
    )
    List<BookingTransitionProjection> transitionRows(String state, String note, long actor, long tripId, int stopOrder);

    @Transactional(propagation = Propagation.MANDATORY)
    default void cancelled(long tripId, String reason, long actor) {
        transition(tripId, 0, BookingStatus.CANCELLED, reason, actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    default void departed(long tripId, int order, long actor) {
        transition(
            tripId,
            order,
            BookingStatus.REJECTED,
            "Điểm đón đã được tài xế xác nhận rời / Pickup stop departure confirmed",
            actor
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    default void arrived(long tripId, int order, long actor) {
        transition(
            tripId,
            order,
            BookingStatus.COMPLETED,
            "Đã đến điểm trả theo xác nhận tài xế / Driver confirmed arrival at drop-off",
            actor
        );
    }

    private void transition(long tripId, int order, BookingStatus state, String note, long actor) {
        BookingTransitionProjection row = transitionRows(state.name(), note, actor, tripId, order).get(0);
        BusinessRules.require(
            row.getTargetCount() == row.getChangedCount() && row.getTargetCount() == row.getLoggedCount(),
            ErrorCode.STALE_VERSION
        );
    }
}
