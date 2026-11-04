package vn.vietroute.business.trip.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.trip.dto.projection.RouteStopProjection;
import vn.vietroute.business.trip.dto.request.RouteRequest;
import vn.vietroute.business.trip.dto.response.RouteResponse;
import vn.vietroute.business.trip.dto.response.RouteStopResponse;
import vn.vietroute.business.trip.entity.Route;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của RouteRepo; transaction ghi thuộc Service.
 */
public interface RouteRepo extends Repository<Route, Long> {
    String FROM = """
    FROM {h-schema}carrier_routes r
    JOIN {h-schema}carriers owner ON owner.id = r.carrier_id AND owner.scope_id = r.scope_id
    """;

    String COLUMNS =
        """
        r.id, r.carrier_id, r.scope_id, owner.name AS carrier_name, r.code, r.name_vi, r.name_en, r.description,
        r.active, r.version, r.created_at, r.updated_at, r.created_by, r.updated_by, (
            SELECT COUNT(*)
            FROM {h-schema}carrier_route_stops s
            WHERE s.route_id = r.id
        ) AS stop_count, (
        """ +
        UserRoleRepo.UPDATE_VISIBLE +
        ") AS can_update ";

    String FILTER =
        FROM +
        "WHERE " +
        UserRoleRepo.VISIBLE +
        """
        AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND LOWER(CONCAT_WS(' ', r.code, r.name_vi, r.name_en, owner.name)) LIKE :keyword ESCAPE '\\'
        """;

    @Query(value = "SELECT COUNT(*) " + FILTER, nativeQuery = true)
    long countRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword
    );

    @Query(
        value = "SELECT " + COLUMNS + FILTER + "ORDER BY r.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> pageRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword,
        String updateTarget,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<RouteResponse> page(CarrierSearch search) {
        UserPrincipal actor = UserPrincipal.current();
        String text = search.getQuery() == null ? "" : search.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        long total = countRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            search.getActive(),
            search.getCarrierId(),
            keyword
        );
        List<RouteResponse> result = pageRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            search.getActive(),
            search.getCarrierId(),
            keyword,
            "PUT " + Routes.ADMIN_ROUTES + Routes.BY_ID,
            search.getSize(),
            search.offset()
        )
            .stream()
            .map(row -> map(row, List.of()))
            .toList();
        return PageResponse.of(result, search, total);
    }

    @Query(value = "SELECT " + COLUMNS + FROM + "WHERE r.id = :routeId AND " + UserRoleRepo.VISIBLE, nativeQuery = true)
    Map<String, Object> detailRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long routeId
    );

    @Query(
        value = "SELECT " + COLUMNS + FROM + "WHERE r.id = :routeId AND " + UserRoleRepo.VISIBLE + "FOR UPDATE OF r ",
        nativeQuery = true
    )
    Map<String, Object> lockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long routeId
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default RouteResponse find(long id, boolean lock, boolean detail) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_ROUTES + Routes.BY_ID;
        Map<String, Object> row = lock
            ? lockedRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id)
            : detailRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id);
        if (row == null || row.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return map(row, detail ? stops(id) : List.of());
    }

    @Query(
        value = """
        SELECT s.stop_order AS "stopOrder", s.location_id AS "locationId", l.name AS "nameVi",
            l.props->> 'nameEn' AS "nameEn", l.props->> 'address' AS "address",
            (l.props->> 'latitude')::NUMERIC AS "latitude", (l.props->> 'longitude')::NUMERIC AS "longitude",
            s.arrival_offset_minutes AS "arrivalOffsetMinutes",
            s.departure_offset_minutes AS "departureOffsetMinutes", s.cumulative_fare AS "cumulativeFare",
            s.pickup_allowed AS "pickupAllowed", s.dropoff_allowed AS "dropoffAllowed", p.id AS "provinceId",
            p.name AS "provinceName", w.id AS "wardId", w.name AS "wardName"
        FROM {h-schema}carrier_route_stops s
        JOIN {h-schema}category l ON l.type= 'VALUE' AND l.ref_type= 'LOCATION' AND l.id = s.location_id
        JOIN {h-schema}category w ON w.id=l.parent_id AND w.ref_type= 'WARD'
        JOIN {h-schema}category p ON p.id=w.parent_id AND p.ref_type= 'PROVINCE'
        WHERE s.route_id = :routeId
        ORDER BY s.stop_order
        """,
        nativeQuery = true
    )
    List<RouteStopProjection> stopRows(long routeId);

    default List<RouteStopResponse> stops(long routeId) {
        return stopRows(routeId)
            .stream()
            .map(rows -> {
                return new RouteStopResponse(
                    rows.getStopOrder(),
                    rows.getLocationId(),
                    rows.getNameVi(),
                    rows.getNameEn(),
                    rows.getAddress(),
                    rows.getLatitude(),
                    rows.getLongitude(),
                    rows.getArrivalOffsetMinutes(),
                    rows.getDepartureOffsetMinutes(),
                    rows.getCumulativeFare(),
                    rows.getPickupAllowed(),
                    rows.getDropoffAllowed(),
                    rows.getProvinceId(),
                    rows.getProvinceName(),
                    rows.getWardId(),
                    rows.getWardName()
                );
            })
            .toList();
    }

    @Query(
        value = """
        SELECT l.id AS "locationId", l.active AND w.active AND p.active AS "usable"
        FROM {h-schema}category l
        JOIN {h-schema}category w ON w.type= 'VALUE' AND w.ref_type= 'WARD' AND w.id=l.parent_id
        JOIN {h-schema}category p ON p.type= 'VALUE' AND p.ref_type= 'PROVINCE' AND p.id=w.parent_id
        WHERE l.id = ANY(:ids)
        ORDER BY l.id
        FOR SHARE OF p, w, l
        """,
        nativeQuery = true
    )
    List<RouteStopProjection> locationRows(Long[] ids);

    @Transactional(propagation = Propagation.MANDATORY)
    default void validateLocations(RouteRequest body) {
        Long[] ids = body
            .getStops()
            .stream()
            .map(stop -> stop.getLocationId())
            .sorted()
            .toArray(Long[]::new);
        List<RouteStopProjection> rows = locationRows(ids);
        BusinessRules.require(rows.size() == ids.length, ErrorCode.ROUTE_INVALID);
        if (body.isActive()) BusinessRules.require(rows.stream().allMatch(row -> row.getUsable()), ErrorCode.CONFLICT);
    }

    @Query(
        value = """
        INSERT INTO {h-schema}carrier_routes(carrier_id, scope_id, code, name_vi, name_en, description, active,
            created_by, updated_by)
        VALUES (:carrierId, :scopeId, :code, :nameVi, :nameEn, :description, :active, :actor, :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertRow(
        Long carrierId,
        long scopeId,
        String code,
        String nameVi,
        String nameEn,
        String description,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insert(long scope, RouteRequest body, long actor) {
        return insertRow(
            body.getCarrierId(),
            scope,
            body.getCode(),
            body.getNameVi().strip(),
            body.getNameEn().strip(),
            body.getDescription().strip(),
            body.isActive(),
            actor
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_routes
        SET name_vi = :nameVi, name_en = :nameEn, description = :description, active = :active, updated_by = :actor,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :id AND carrier_id = :carrierId AND code = :code AND version = :version
        """,
        nativeQuery = true
    )
    int updateRow(
        long id,
        Long carrierId,
        String code,
        String nameVi,
        String nameEn,
        String description,
        boolean active,
        Long version,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void update(long id, RouteRequest body, long actor) {
        BusinessRules.require(
            updateRow(
                id,
                body.getCarrierId(),
                body.getCode(),
                body.getNameVi().strip(),
                body.getNameEn().strip(),
                body.getDescription().strip(),
                body.isActive(),
                body.getVersion(),
                actor
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    private static RouteResponse map(Map<String, Object> row, List<RouteStopResponse> stops) {
        return new RouteResponse(
            longValue(row.get("id")),
            longValue(row.get("carrier_id")),
            longValue(row.get("scope_id")),
            stringValue(row.get("carrier_name")),
            stringValue(row.get("code")),
            stringValue(row.get("name_vi")),
            stringValue(row.get("name_en")),
            stringValue(row.get("description")),
            booleanValue(row.get("active")),
            intValue(row.get("stop_count")),
            longValue(row.get("version")),
            instantValue(row.get("created_at")),
            instantValue(row.get("updated_at")),
            longValue(row.get("created_by")),
            longValue(row.get("updated_by")),
            booleanValue(row.get("can_update")),
            stops
        );
    }
}
