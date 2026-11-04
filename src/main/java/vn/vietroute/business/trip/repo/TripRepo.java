package vn.vietroute.business.trip.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.trip.constant.TripChoiceKind;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.projection.RouteStopProjection;
import vn.vietroute.business.trip.dto.projection.TripChoiceProjection;
import vn.vietroute.business.trip.dto.projection.TripDriverProjection;
import vn.vietroute.business.trip.dto.projection.TripEventProjection;
import vn.vietroute.business.trip.dto.projection.TripStopProjection;
import vn.vietroute.business.trip.dto.projection.TripTemplateProjection;
import vn.vietroute.business.trip.dto.projection.TripVehicleProjection;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripEventResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.business.trip.dto.search.TripChoiceSearch;
import vn.vietroute.business.trip.dto.search.TripSearch;
import vn.vietroute.business.trip.entity.Trip;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của TripRepo; transaction ghi thuộc Service.
 */
public interface TripRepo extends Repository<Trip, Long> {
    String FROM = """
    FROM {h-schema}carrier_trips t
    JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
    """;

    String COLUMNS =
        """
        t.id, t.carrier_id, t.scope_id, owner.name AS carrier_name, owner.phone AS carrier_phone, t.route_id,
        t.code, t.name_vi, t.name_en, t.vehicle_id, t.plate_number, t.vehicle_type_id, t.vehicle_type_name_vi,
        t.vehicle_type_name_en, t.driver_id, t.driver_user_id, t.driver_name, t.driver_phone, t.departure_at,
        t.end_at, t.capacity, t.status, t.operation_note, t.version, (
        """ +
        UserRoleRepo.UPDATE_VISIBLE +
        ") AS can_update ";

    String ASSIGNED = """
        AND (CAST(:driverUserId AS BIGINT) IS NULL OR (t.status <> 'DRAFT'
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
    )))
    """;

    String FILTER =
        FROM +
        "WHERE " +
        UserRoleRepo.VISIBLE +
        """
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND (CAST(:status AS TEXT) IS NULL OR t.status = :status)
        AND (CAST(:fromAt AS TIMESTAMPTZ) IS NULL OR (t.departure_at >= :fromAt AND t.departure_at < :toAt))
        AND STRPOS(LOWER(CONCAT_WS(' ', t.code, t.name_vi, t.name_en, t.plate_number, owner.name)),
        :keyword) > 0
        """ +
        ASSIGNED;

    @Query(value = "SELECT COUNT(*) " + FILTER, nativeQuery = true)
    long countRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String status,
        Instant fromAt,
        Instant toAt,
        String keyword,
        Long driverUserId
    );

    @Query(
        value = "SELECT " +
            COLUMNS +
            FILTER +
            "ORDER BY t.departure_at DESC, t.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> pageRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String status,
        Instant fromAt,
        Instant toAt,
        String keyword,
        Long driverUserId,
        String updateTarget,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<TripResponse> page(TripSearch body) {
        return page(body, null);
    }

    @Transactional(readOnly = true)
    default PageResponse<TripResponse> assignedPage(TripSearch body, long driverUserId) {
        return page(body, driverUserId);
    }

    private PageResponse<TripResponse> page(TripSearch body, Long driverUserId) {
        UserPrincipal actor = UserPrincipal.current();
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        Instant from = body.getDate() == null ? null : body.getDate().atStartOfDay(zone).toInstant();
        Instant to = body.getDate() == null ? null : body.getDate().plusDays(1).atStartOfDay(zone).toInstant();
        String status = body.getStatus() == null ? null : body.getStatus().name();
        String keyword = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total = countRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getCarrierId(),
            status,
            from,
            to,
            keyword,
            driverUserId
        );
        List<TripResponse> rows = pageRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getCarrierId(),
            status,
            from,
            to,
            keyword,
            driverUserId,
            "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(row -> TripResponse.from(row, List.of(), List.of()))
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = "SELECT " + COLUMNS + FROM + "WHERE t.id = :tripId AND " + UserRoleRepo.VISIBLE + ASSIGNED,
        nativeQuery = true
    )
    Map<String, Object> detailRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long tripId,
        Long driverUserId
    );

    @Query(
        value = "SELECT " +
            COLUMNS +
            FROM +
            "WHERE t.id = :tripId AND " +
            UserRoleRepo.VISIBLE +
            ASSIGNED +
            "FOR UPDATE OF t ",
        nativeQuery = true
    )
    Map<String, Object> lockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long tripId,
        Long driverUserId
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default TripResponse find(long id, boolean lock, boolean detail) {
        return detail(id, lock, detail, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    default TripResponse assigned(long id, boolean lock, long driverUserId) {
        return detail(id, lock, true, driverUserId);
    }

    private TripResponse detail(long id, boolean lock, boolean withChildren, Long driverUserId) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_TRIPS + Routes.BY_ID;
        Map<String, Object> row = lock
            ? lockedRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id, driverUserId)
            : detailRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id, driverUserId);
        if (row == null || row.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return TripResponse.from(row, withChildren ? stops(id) : List.of(), withChildren ? events(id) : List.of());
    }

    @Query(
        value = """
        SELECT id, location_id AS "locationId", province_id AS "provinceId", stop_order AS "stopOrder",
            name_vi AS "nameVi", name_en AS "nameEn", address, latitude,
            longitude, scheduled_arrival_at AS "scheduledArrivalAt",
            scheduled_departure_at AS "scheduledDepartureAt", actual_arrival_at AS "actualArrivalAt",
            actual_departure_at AS "actualDepartureAt", cumulative_fare AS "cumulativeFare",
            pickup_allowed AS "pickupAllowed", dropoff_allowed AS "dropoffAllowed"
        FROM {h-schema}trip_stops
        WHERE trip_id = :tripId
        ORDER BY stop_order
        """,
        nativeQuery = true
    )
    List<TripStopProjection> stopsRows(@Param("tripId") long id);

    default List<TripStopResponse> stops(long id) {
        List<TripStopResponse> rows = stopsRows(id).stream().map(TripStopResponse::from).toList();
        return rows;
    }

    @Query(
        value = """
        SELECT id, event_type AS "eventType", stop_order AS "stopOrder", note, created_at AS "createdAt"
        FROM (
            SELECT id, event_type, stop_order, note, created_at
            FROM {h-schema}trip_events
            WHERE trip_id = :tripId
            ORDER BY id DESC
            LIMIT 100
        ) e
        ORDER BY id
        """,
        nativeQuery = true
    )
    List<TripEventProjection> eventsRows(@Param("tripId") long id);

    private static TripEventResponse eventProjection(TripEventProjection row) {
        return new TripEventResponse(
            row.getId(),
            row.getEventType(),
            row.getStopOrder(),
            row.getNote(),
            row.getCreatedAt()
        );
    }

    private List<TripEventResponse> events(long id) {
        List<TripEventResponse> rows = eventsRows(id).stream().map(TripRepo::eventProjection).toList();
        return rows;
    }

    @Query(
        value = """
        SELECT l.id AS "locationId", p.id AS "provinceId", s.stop_order AS "stopOrder", l.name AS "nameVi",
            l.props->> 'nameEn' AS "nameEn", l.props->> 'address' AS "address",
            (l.props->> 'latitude')::NUMERIC AS "latitude", (l.props->> 'longitude')::NUMERIC AS "longitude",
            s.arrival_offset_minutes AS "arrivalOffsetMinutes",
            s.departure_offset_minutes AS "departureOffsetMinutes", s.cumulative_fare AS "cumulativeFare",
            s.pickup_allowed AS "pickupAllowed", s.dropoff_allowed AS "dropoffAllowed",
            l.active AND w.active AND p.active AS "usable"
        FROM {h-schema}carrier_route_stops s
        JOIN {h-schema}category l ON l.type= 'VALUE' AND l.ref_type= 'LOCATION' AND l.id = s.location_id
        JOIN {h-schema}category w ON w.type= 'VALUE' AND w.ref_type= 'WARD' AND w.id=l.parent_id
        JOIN {h-schema}category p ON p.type= 'VALUE' AND p.ref_type= 'PROVINCE' AND p.id=w.parent_id
        WHERE s.route_id = :routeId
        ORDER BY s.stop_order
        FOR SHARE OF p, w, l
        """,
        nativeQuery = true
    )
    List<RouteStopProjection> sourceStopsRows(@Param("routeId") long id);

    private static SourceStop sourceProjection(RouteStopProjection row) {
        return new SourceStop(
            row.getLocationId(),
            row.getProvinceId(),
            row.getStopOrder(),
            row.getNameVi(),
            row.getNameEn(),
            row.getAddress(),
            row.getLatitude(),
            row.getLongitude(),
            row.getArrivalOffsetMinutes(),
            row.getDepartureOffsetMinutes(),
            row.getCumulativeFare(),
            row.getPickupAllowed(),
            row.getDropoffAllowed(),
            row.getUsable()
        );
    }

    private List<SourceStop> sourceStops(long id) {
        List<SourceStop> rows = sourceStopsRows(id).stream().map(TripRepo::sourceProjection).toList();
        BusinessRules.require(rows.size() >= 2 && rows.size() <= 32, ErrorCode.TRIP_INVALID);
        return rows;
    }

    @Query(
        value = """
        INSERT INTO {h-schema}carrier_trips(carrier_id, scope_id, route_id, vehicle_id, driver_id, driver_user_id,
            code, name_vi, name_en, plate_number, vehicle_type_id, vehicle_type_name_vi,
            vehicle_type_name_en, driver_name, driver_phone, departure_at, end_at, capacity, created_by, updated_by)
        VALUES (:carrierId, :scopeId, :templateId, :vehicleId, :driverId, :driverUserId, :code, :nameVi, :nameEn,
                :plate, :typeId, :typeVi, :typeEn, :driverName, :driverPhone, :departureAt, :endAt, :capacity, :actor,
                :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertRow(
        long carrierId,
        long scopeId,
        long templateId,
        long vehicleId,
        long driverId,
        long driverUserId,
        String code,
        String nameVi,
        String nameEn,
        String plate,
        long typeId,
        String typeVi,
        String typeEn,
        String driverName,
        String driverPhone,
        Instant departureAt,
        Instant endAt,
        int capacity,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insert(TripRequest body, Template template, Assets assets, Instant end, long actor) {
        return insertRow(
            template.getCarrierId(),
            template.getScopeId(),
            template.getId(),
            assets.getVehicleId(),
            assets.getDriverId(),
            assets.getDriverUserId(),
            body.getCode(),
            template.getNameVi(),
            template.getNameEn(),
            assets.getPlate(),
            assets.getTypeId(),
            assets.getTypeVi(),
            assets.getTypeEn(),
            assets.getDriverName(),
            assets.getDriverPhone(),
            body.getDepartureAt(),
            end,
            body.getCapacity(),
            actor
        );
    }

    @Query(
        value = """
        SELECT l.active
            AND w.active AND p.active
        FROM {h-schema}trip_stops s
        JOIN {h-schema}category l ON l.type= 'VALUE' AND l.ref_type= 'LOCATION' AND l.id = s.location_id
        JOIN {h-schema}category w ON w.type= 'VALUE' AND w.ref_type= 'WARD' AND w.id=l.parent_id
        JOIN {h-schema}category p ON p.type= 'VALUE' AND p.ref_type= 'PROVINCE' AND p.id = s.province_id
        WHERE s.trip_id = :id
        ORDER BY s.stop_order
        FOR SHARE OF p, w, l
        """,
        nativeQuery = true
    )
    List<Boolean> activeStops(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default void requireStopsActive(long id) {
        BusinessRules.require(activeStops(id).stream().allMatch(Boolean.TRUE::equals), ErrorCode.TRIP_STATE_CONFLICT);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_trips
        SET vehicle_id = :vehicleId, driver_id = :driverId, driver_user_id = :driverUserId, plate_number = :plate,
            vehicle_type_id = :typeId,
            vehicle_type_name_vi = :typeVi, vehicle_type_name_en = :typeEn, driver_name = :driverName, driver_phone =
                :driverPhone,
            capacity = :capacity, departure_at = :departureAt, end_at = :endAt, updated_by = :actor, updated_at =
                CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :tripVersion
        """,
        nativeQuery = true
    )
    int assignmentRow(
        long vehicleId,
        long driverId,
        long driverUserId,
        String plate,
        long typeId,
        String typeVi,
        String typeEn,
        String driverName,
        String driverPhone,
        int capacity,
        Instant departureAt,
        Instant endAt,
        long actor,
        long tripId,
        long carrierId,
        long scopeId,
        long tripVersion
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void assignment(
        TripResponse trip,
        Assets assets,
        int capacity,
        Instant departure,
        Instant end,
        long actor
    ) {
        BusinessRules.require(
            assignmentRow(
                assets.getVehicleId(),
                assets.getDriverId(),
                assets.getDriverUserId(),
                assets.getPlate(),
                assets.getTypeId(),
                assets.getTypeVi(),
                assets.getTypeEn(),
                assets.getDriverName(),
                assets.getDriverPhone(),
                capacity,
                departure,
                end,
                actor,
                trip.getId(),
                trip.getCarrierId(),
                trip.getScopeId(),
                trip.getVersion()
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_trips
        SET status = :status, operation_note = :note, updated_by = :actor,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId AND version = :tripVersion
        """,
        nativeQuery = true
    )
    int stateRow(String status, String note, long actor, long tripId, long carrierId, long scopeId, long tripVersion);

    @Transactional(propagation = Propagation.MANDATORY)
    default void state(TripResponse trip, TripStatus status, String note, long actor) {
        BusinessRules.require(
            stateRow(
                status.name(),
                note,
                actor,
                trip.getId(),
                trip.getCarrierId(),
                trip.getScopeId(),
                trip.getVersion()
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO {h-schema}trip_events(trip_id, carrier_id, scope_id, event_type, stop_order, note, created_by)
        VALUES (:tripId, :carrierId, :scopeId, :type, :order, :note, :actor)
        """,
        nativeQuery = true
    )
    int insertEvent(long tripId, long carrierId, long scopeId, String type, Integer order, String note, long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    default void event(long tripId, long carrierId, long scopeId, String type, Integer order, String note, long actor) {
        BusinessRules.require(
            insertEvent(tripId, carrierId, scopeId, type, order, note, actor) == 1,
            ErrorCode.DATABASE_UNAVAILABLE
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

    @Query(
        value = """
        SELECT id, carrier_id AS "carrierId", scope_id AS "scopeId", name_vi AS "nameVi",
            name_en AS "nameEn", active
        FROM {h-schema}carrier_routes
        WHERE id = :routeId
            AND (CAST(:carrierId AS BIGINT) IS NULL OR carrier_id = :carrierId)
        """,
        nativeQuery = true
    )
    List<TripTemplateProjection> templateRows(long routeId, Long carrierId);

    @Query(
        value = """
        SELECT id, carrier_id AS "carrierId", scope_id AS "scopeId", name_vi AS "nameVi",
            name_en AS "nameEn", active
        FROM {h-schema}carrier_routes
        WHERE id = :routeId
            AND (CAST(:carrierId AS BIGINT) IS NULL OR carrier_id = :carrierId)
        FOR SHARE
        """,
        nativeQuery = true
    )
    List<TripTemplateProjection> lockedTemplateRows(long routeId, Long carrierId);

    @Transactional(propagation = Propagation.MANDATORY)
    default Template template(long routeId, Long carrierId, boolean lock) {
        List<TripTemplateProjection> rows = lock
            ? lockedTemplateRows(routeId, carrierId)
            : templateRows(routeId, carrierId);
        BusinessRules.require(!rows.isEmpty(), ErrorCode.NOT_FOUND);
        TripTemplateProjection row = rows.get(0);
        return new Template(
            row.getId(),
            row.getCarrierId(),
            row.getScopeId(),
            row.getNameVi(),
            row.getNameEn(),
            row.getActive(),
            lock ? sourceStops(routeId) : List.of()
        );
    }

    @Query(
        value = """
        SELECT v.plate_number AS "plateNumber", v.vehicle_type_id AS "vehicleTypeId", t.name AS "vehicleTypeNameVi",
            t.props->> 'nameEn' AS "vehicleTypeNameEn", v.capacity
        FROM {h-schema}carrier_vehicles v
        JOIN {h-schema}category t ON t.id = v.vehicle_type_id
            AND t.ref_type = 'VEHICLE_TYPE' AND t.type= 'VALUE'
            AND t.active
        WHERE v.id = :vehicleId AND v.carrier_id = :carrierId AND v.scope_id = :scopeId AND v.active
        FOR UPDATE OF v
        FOR SHARE OF t
        """,
        nativeQuery = true
    )
    List<TripVehicleProjection> vehicleAssets(long vehicleId, long carrierId, long scopeId);

    @Query(
        value = """
        SELECT user_id AS "userId", name, phone
        FROM {h-schema}carrier_drivers
        WHERE id = :driverId AND carrier_id = :carrierId AND scope_id = :scopeId AND active AND user_id IS NOT NULL
        FOR UPDATE
        """,
        nativeQuery = true
    )
    List<TripDriverProjection> driverAssets(long driverId, long carrierId, long scopeId);

    @Transactional(propagation = Propagation.MANDATORY)
    default Assets assets(long carrierId, long scopeId, long vehicleId, long driverId) {
        List<TripVehicleProjection> vehicles = vehicleAssets(vehicleId, carrierId, scopeId);
        BusinessRules.require(!vehicles.isEmpty(), ErrorCode.TRIP_INVALID);
        TripVehicleProjection v = vehicles.get(0);
        List<TripDriverProjection> drivers = driverAssets(driverId, carrierId, scopeId);
        BusinessRules.require(!drivers.isEmpty(), ErrorCode.TRIP_INVALID);
        TripDriverProjection d = drivers.get(0);
        return new Assets(
            vehicleId,
            driverId,
            d.getUserId(),
            v.getPlateNumber(),
            v.getVehicleTypeId(),
            v.getVehicleTypeNameVi(),
            v.getVehicleTypeNameEn(),
            v.getCapacity(),
            d.getName(),
            d.getPhone()
        );
    }

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM {h-schema}carrier_trips
            WHERE id <> :exceptId
                AND status IN ('OPEN', 'IN_PROGRESS')
                AND (vehicle_id = :vehicleId OR driver_id = :driverId OR driver_user_id = :driverUserId)
                AND ((departure_at < :rangeEnd AND end_at > :rangeStart) OR (:starting AND status = 'IN_PROGRESS'))
        )
        """,
        nativeQuery = true
    )
    boolean scheduleConflict(
        long exceptId,
        long vehicleId,
        long driverId,
        long driverUserId,
        Instant rangeEnd,
        Instant rangeStart,
        boolean starting
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void requireAvailable(long exceptId, Assets assets, Instant start, Instant end, boolean starting) {
        BusinessRules.require(
            !scheduleConflict(
                exceptId,
                assets.getVehicleId(),
                assets.getDriverId(),
                assets.getDriverUserId(),
                end,
                start,
                starting
            ),
            ErrorCode.TRIP_SCHEDULE_CONFLICT
        );
    }

    String CHOICE_CARRIER =
        "FROM {h-schema}carriers owner WHERE " +
        UserRoleRepo.VISIBLE +
        """
        AND owner.active
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND STRPOS(LOWER(CONCAT_WS(' ', owner.code, owner.name)), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + CHOICE_CARRIER, nativeQuery = true)
    long choiceCarrierCount(long viewerId, long viewerVersion, String viewTarget, Long carrierId, String keyword);

    @Query(
        value = "SELECT owner.id, owner.code, owner.name, NULL::INTEGER AS \"capacity\" " +
            CHOICE_CARRIER +
            "ORDER BY owner.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<TripChoiceProjection> choiceCarrierRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String keyword,
        int pageSize,
        long offset
    );

    String CHOICE_ROUTE =
        """
        FROM {h-schema}carrier_routes item
        JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
        AND owner.active AND item.active
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND STRPOS(LOWER(CONCAT_WS(' ', item.code, CONCAT_WS(' / ', item.name_vi, item.name_en))), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + CHOICE_ROUTE, nativeQuery = true)
    long choiceRouteCount(long viewerId, long viewerVersion, String viewTarget, Long carrierId, String keyword);

    @Query(
        value = """
        SELECT item.id, item.code, CONCAT_WS(' / ', item.name_vi, item.name_en) AS "name",
            NULL::INTEGER AS "capacity"
        """ +
            CHOICE_ROUTE +
            "ORDER BY item.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<TripChoiceProjection> choiceRouteRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String keyword,
        int pageSize,
        long offset
    );

    String CHOICE_VEHICLE =
        """
        FROM {h-schema}carrier_vehicles item
        JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
        JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type= 'VEHICLE_TYPE' AND vt.type= 'VALUE'
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
        AND owner.active AND item.active AND vt.active
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND STRPOS(LOWER(CONCAT_WS(' ', item.code, item.plate_number)), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + CHOICE_VEHICLE, nativeQuery = true)
    long choiceVehicleCount(long viewerId, long viewerVersion, String viewTarget, Long carrierId, String keyword);

    @Query(
        value = "SELECT item.id, item.code, item.plate_number AS \"name\" , item.capacity " +
            CHOICE_VEHICLE +
            "ORDER BY item.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<TripChoiceProjection> choiceVehicleRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String keyword,
        int pageSize,
        long offset
    );

    String CHOICE_DRIVER =
        """
        FROM {h-schema}carrier_drivers item
        JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
        JOIN {h-schema}auth_users du ON du.id = item.user_id
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
            AND owner.active
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
            AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            AND STRPOS(LOWER(CONCAT_WS(' ', item.code, item.name)), :keyword) > 0
        """;

    @Query(value = "SELECT COUNT(*) " + CHOICE_DRIVER, nativeQuery = true)
    long choiceDriverCount(long viewerId, long viewerVersion, String viewTarget, Long carrierId, String keyword);

    @Query(
        value = "SELECT item.id, item.code, item.name, NULL::INTEGER AS \"capacity\" " +
            CHOICE_DRIVER +
            "ORDER BY item.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<TripChoiceProjection> choiceDriverRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Long carrierId,
        String keyword,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<TripChoiceResponse> choices(TripChoiceSearch body) {
        BusinessRules.require(
            body.getKind() == TripChoiceKind.CARRIER || body.getCarrierId() != null,
            ErrorCode.INVALID_REQUEST
        );
        UserPrincipal actor = UserPrincipal.current();
        String keyword = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long viewer = actor.id();
        long version = actor.tokenVersion();
        String target = actor.getTarget();
        Long carrier = body.getCarrierId();
        int size = body.getSize();
        long offset = body.offset();
        long total;
        List<TripChoiceProjection> rows;
        switch (body.getKind()) {
            case CARRIER -> {
                total = choiceCarrierCount(viewer, version, target, carrier, keyword);
                rows = choiceCarrierRows(viewer, version, target, carrier, keyword, size, offset);
            }
            case ROUTE -> {
                total = choiceRouteCount(viewer, version, target, carrier, keyword);
                rows = choiceRouteRows(viewer, version, target, carrier, keyword, size, offset);
            }
            case VEHICLE -> {
                total = choiceVehicleCount(viewer, version, target, carrier, keyword);
                rows = choiceVehicleRows(viewer, version, target, carrier, keyword, size, offset);
            }
            case DRIVER -> {
                total = choiceDriverCount(viewer, version, target, carrier, keyword);
                rows = choiceDriverRows(viewer, version, target, carrier, keyword, size, offset);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return PageResponse.of(rows.stream().map(TripChoiceResponse::from).toList(), body, total);
    }
}
