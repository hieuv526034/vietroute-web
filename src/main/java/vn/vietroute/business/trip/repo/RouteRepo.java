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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.trip.dto.request.RouteRequest;
import vn.vietroute.business.trip.dto.response.RouteResponse;
import vn.vietroute.business.trip.dto.response.RouteStopResponse;
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** SQL tuyến luôn ràng ID + nhà xe/scope và cùng predicate cho count/trang/chi tiết. */
@Repository
public class RouteRepo extends BaseRepository {

    private static final String FROM = """
    FROM {h-schema}carrier_routes r
    JOIN {h-schema}carriers owner ON owner.id = r.carrier_id AND owner.scope_id = r.scope_id
    """;
    private static final String COLUMNS = """
    r.id, r.carrier_id, r.scope_id, owner.name AS carrier_name, r.code, r.name_vi, r.name_en, r.description,
        r.active, r.version, r.created_at, r.updated_at, r.created_by, r.updated_by, (
            SELECT COUNT(*)
            FROM {h-schema}carrier_route_stops s
            WHERE s.route_id = r.id
        ) AS stop_count, (%s) AS can_update
    """.formatted(UserRoleRepo.visibleSql("updateTarget"));

    public RouteRepo(EntityManager manager) {
        super(manager);
    }

    /** Tìm nguyên văn với SQL parameters; dữ liệu ngoài quyền không tham gia tổng số. */
    @Transactional(readOnly = true)
    public PageResponse<RouteResponse> page(CarrierSearch search) {
        String filter = """
        %s
        WHERE %s
            AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
            AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            AND LOWER(CONCAT_WS(' ', r.code, r.name_vi, r.name_en, owner.name)) LIKE :keyword ESCAPE '\\'
        """.formatted(FROM, UserRoleRepo.visibleSql());
        long count;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            bind(query, search);
            {
                Tuple rows = (Tuple) query.getSingleResult();
                count = longValue(rows.get(0));
            }
        }
        ArrayList<RouteResponse> items = new ArrayList<RouteResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                ORDER BY r.id DESC
                LIMIT :pageSize OFFSET :offset
                """.formatted(COLUMNS, filter),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query::setParameter, "PUT " + Routes.ADMIN_ROUTES + Routes.BY_ID);
            bind(query, search);
            query.setParameter("pageSize", search.getSize());
            query.setParameter("offset", search.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    items.add(map(rows, List.of()));
                }
            }
        }
        return PageResponse.of(items, search, count);
    }

    private void bind(Query query, CarrierSearch search) {
        UserRoleRepo.bindVisible(query::setParameter);
        query.setParameter("active", search.getActive());
        query.setParameter("carrierId", search.getCarrierId());
        String text = search.getQuery() == null ? "" : search.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setParameter("keyword", "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }

    /** Action do service truyền; đọc lại guard scoped khi khóa dòng, không tin carrierId từ body. */
    public RouteResponse find(long id, boolean lock, boolean detail) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                WHERE r.id = :routeId
                    AND %s
                %s
                """.formatted(COLUMNS, FROM, UserRoleRepo.visibleSql(), lock ? " FOR UPDATE OF r" : ""),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query::setParameter, "PUT " + Routes.ADMIN_ROUTES + Routes.BY_ID);
            query.setParameter("routeId", id);
            UserRoleRepo.bindVisible(query::setParameter);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return map(row, detail ? stops(id) : List.of());
            }
        }
    }

    /** Caller đã kiểm và khóa tuyến; các điểm chỉ được truy cập qua parent server. */
    public List<RouteStopResponse> stops(long routeId) {
        ArrayList<RouteStopResponse> result = new ArrayList<RouteStopResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT s.stop_order, s.location_id, l.name AS name_vi, l.props->>'nameEn' AS name_en, l.props->>'address' AS address, (l.props->>'latitude')::NUMERIC AS latitude, (l.props->>'longitude')::NUMERIC AS longitude,
                    s.arrival_offset_minutes,
                    s.departure_offset_minutes, s.cumulative_fare, s.pickup_allowed, s.dropoff_allowed,
                    p.id, p.name, w.id, w.name
                FROM {h-schema}carrier_route_stops s
                JOIN {h-schema}category l ON l.type='VALUE' AND l.ref_type='LOCATION' AND l.id = s.location_id
                JOIN {h-schema}category w ON w.id=l.parent_id AND w.ref_type='WARD'
                JOIN {h-schema}category p ON p.id=w.parent_id AND p.ref_type='PROVINCE'
                WHERE s.route_id = :routeId
                ORDER BY s.stop_order
                """,
                Tuple.class
            );

            query.setParameter("routeId", routeId);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(
                        new RouteStopResponse(
                            intValue(rows.get(0)),
                            longValue(rows.get(1)),
                            stringValue(rows.get(2)),
                            stringValue(rows.get(3)),
                            stringValue(rows.get(4)),
                            decimalValue(rows.get(5)),
                            decimalValue(rows.get(6)),
                            intValue(rows.get(7)),
                            intValue(rows.get(8)),
                            decimalValue(rows.get(9)),
                            booleanValue(rows.get(10)),
                            booleanValue(rows.get(11)),
                            longValue(rows.get(12)),
                            stringValue(rows.get(13)),
                            longValue(rows.get(14)),
                            stringValue(rows.get(15))
                        )
                    );
                }
            }
        }
        return List.copyOf(result);
    }

    /** Khóa chia sẻ địa điểm/tỉnh trong cùng transaction để không chốt tuyến active với danh mục vừa bị tắt. */
    public void validateLocations(RouteRequest body) {
        Long[] ids = body
            .getStops()
            .stream()
            .map(stop -> stop.getLocationId())
            .sorted()
            .toArray(Long[]::new);

        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT l.id, l.active
                    AND w.active AND p.active AS usable
                FROM {h-schema}category l
                JOIN {h-schema}category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.id=l.parent_id
                JOIN {h-schema}category p ON p.type='VALUE' AND p.ref_type='PROVINCE' AND p.id=w.parent_id
                WHERE l.id = ANY(:ids)
                ORDER BY l.id
                FOR SHARE OF p, w, l
                """,
                Tuple.class
            );

            query.setParameter("ids", ids);
            int count = 0;
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    count++;
                    if (body.isActive() && !booleanValue(rows.get(1))) throw new BusinessException(ErrorCode.CONFLICT);
                }
            }
            if (count != ids.length) throw new BusinessException(ErrorCode.ROUTE_INVALID);
        }
    }

    /** INSERT parent và các điểm dùng một connection; lỗi ràng buộc rollback toàn bộ. */
    public long insert(long scopeId, RouteRequest body, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}carrier_routes(carrier_id, scope_id, code, name_vi, name_en, description, active,
                    created_by, updated_by)
                VALUES (:carrierId, :scopeId, :code, :nameVi, :nameEn, :description, :active, :actor, :actor)
                RETURNING id
                """,
                Tuple.class
            );

            query.setParameter("carrierId", body.getCarrierId());
            query.setParameter("scopeId", scopeId);
            query.setParameter("code", body.getCode());
            query.setParameter("nameVi", body.getNameVi().strip());
            query.setParameter("nameEn", body.getNameEn().strip());
            query.setParameter("description", body.getDescription().strip());
            query.setParameter("active", body.isActive());
            query.setParameter("actor", actor);
            {
                Tuple row = (Tuple) query.getSingleResult();
                return longValue(row.get(0));
            }
        }
    }

    /** CAS không tự lấy version mới để ghi đè; carrier/code/scope giữ nguyên. */
    public void update(long id, RouteRequest body, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}carrier_routes
                SET name_vi = :nameVi, name_en = :nameEn, description = :description, active = :active, updated_by = :actor,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :id AND carrier_id = :carrierId AND code = :code AND version = :version
                """
            );

            query.setParameter("nameVi", body.getNameVi().strip());
            query.setParameter("nameEn", body.getNameEn().strip());
            query.setParameter("description", body.getDescription().strip());
            query.setParameter("active", body.isActive());
            query.setParameter("actor", actor);
            query.setParameter("id", id);
            query.setParameter("carrierId", body.getCarrierId());
            query.setParameter("code", body.getCode());
            query.setParameter("version", body.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    private RouteResponse map(Tuple row, List<RouteStopResponse> stops) {
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
