package vn.vietroute.business.route.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.route.dto.request.RouteWriteRequest;
import vn.vietroute.business.route.dto.response.RouteResponse;
import vn.vietroute.business.route.dto.response.RouteStopResponse;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** SQL tuyến luôn ràng ID + nhà xe/scope và cùng predicate cho count/trang/chi tiết. */
@Repository
public class RouteRepository extends BaseRepository {

    private static final String FROM =
        " FROM carrier_routes r JOIN carriers owner ON owner.id=r.carrier_id AND owner.scope_id=r.scope_id ";
    private static final String COLUMNS =
        "r.id,r.carrier_id,r.scope_id,owner.name AS carrier_name,r.code,r.name_vi,r.name_en,r.description,r.active,r.version,r.created_at,r.updated_at,r.created_by,r.updated_by,(SELECT count(*) FROM carrier_route_stops s WHERE s.route_id=r.id) AS stop_count,(" +
        DataScopeGuard.visibleSql() +
        ") AS can_update";

    public RouteRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Tìm nguyên văn với SQL parameters; dữ liệu ngoài quyền không tham gia tổng số. */
    public PageResponse<RouteResponse> page(CarrierSearchRequest search) {
        return read(connection -> {
            String filter =
                FROM +
                " WHERE " +
                DataScopeGuard.visibleSql() +
                " AND (?::boolean IS NULL OR r.active=?) AND (?::bigint IS NULL OR owner.id=?) AND lower(concat_ws(' ',r.code,r.name_vi,r.name_en,owner.name)) LIKE ? ESCAPE '\\'";
            long count;
            try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
                bind(query, 1, search);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    count = rows.getLong(1);
                }
            }
            var items = new ArrayList<RouteResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + COLUMNS + filter + " ORDER BY r.id DESC LIMIT ? OFFSET ?"
                )
            ) {
                int at = DataScopeGuard.bindVisible(query, 1, Groups.ROUTE, PermissionAction.UPDATE);
                at = bind(query, at, search);
                query.setInt(at++, search.getSize());
                query.setLong(at, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) items.add(map(rows, List.of()));
                }
            }
            return PageResponse.of(items, search, count);
        });
    }

    private int bind(PreparedStatement query, int at, CarrierSearchRequest search) throws SQLException {
        at = DataScopeGuard.bindVisible(query, at, Groups.ROUTE, PermissionAction.READ);
        query.setObject(at++, search.getActive());
        query.setObject(at++, search.getActive());
        query.setObject(at++, search.getCarrierId());
        query.setObject(at++, search.getCarrierId());
        String text = search.getQuery() == null ? "" : search.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setString(at++, "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return at;
    }

    /** Action do service truyền; đọc lại guard scoped khi khóa dòng, không tin carrierId từ body. */
    public RouteResponse find(Connection connection, long id, PermissionAction action, boolean lock, boolean detail)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT " +
                    COLUMNS +
                    FROM +
                    " WHERE r.id=? AND " +
                    DataScopeGuard.visibleSql() +
                    (lock ? " FOR UPDATE OF r" : "")
            )
        ) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.ROUTE, PermissionAction.UPDATE);
            query.setLong(at++, id);
            DataScopeGuard.bindVisible(query, at, Groups.ROUTE, action);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(row, detail ? stops(connection, id) : List.of());
            }
        }
    }

    /** Caller đã kiểm và khóa tuyến; các điểm chỉ được truy cập qua parent server. */
    public List<RouteStopResponse> stops(Connection connection, long routeId) throws SQLException {
        var result = new ArrayList<RouteStopResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT s.stop_order,s.location_id,l.name_vi,l.name_en,l.address,l.latitude,l.longitude,s.arrival_offset_minutes,s.departure_offset_minutes,s.cumulative_fare,s.pickup_allowed,s.dropoff_allowed FROM carrier_route_stops s JOIN location_places l ON l.id=s.location_id WHERE s.route_id=? ORDER BY s.stop_order"
            )
        ) {
            query.setLong(1, routeId);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    result.add(
                        new RouteStopResponse(
                            rows.getInt(1),
                            rows.getLong(2),
                            rows.getString(3),
                            rows.getString(4),
                            rows.getString(5),
                            rows.getBigDecimal(6),
                            rows.getBigDecimal(7),
                            rows.getInt(8),
                            rows.getInt(9),
                            rows.getBigDecimal(10),
                            rows.getBoolean(11),
                            rows.getBoolean(12)
                        )
                    );
            }
        }
        return List.copyOf(result);
    }

    /** Khóa chia sẻ địa điểm/tỉnh trong cùng transaction để không chốt tuyến active với danh mục vừa bị tắt. */
    public void validateLocations(Connection connection, RouteWriteRequest body) throws SQLException {
        var ids = body
            .getStops()
            .stream()
            .map(stop -> stop.locationId())
            .sorted()
            .toArray(Long[]::new);
        var array = connection.createArrayOf("bigint", ids);
        try (
            var query = connection.prepareStatement(
                "SELECT l.id,l.active AND p.active AS usable FROM location_places l JOIN location_provinces p ON p.id=l.province_id WHERE l.id=ANY(?) ORDER BY l.id FOR SHARE OF p,l"
            )
        ) {
            query.setArray(1, array);
            int count = 0;
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    count++;
                    if (body.isActive() && !rows.getBoolean(2)) throw new BusinessException(ErrorCode.CONFLICT);
                }
            }
            if (count != ids.length) throw new BusinessException(ErrorCode.ROUTE_INVALID);
        } finally {
            array.free();
        }
    }

    /** INSERT parent và các điểm dùng một connection; lỗi ràng buộc rollback toàn bộ. */
    public long insert(Connection connection, long scopeId, RouteWriteRequest body, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carrier_routes(carrier_id,scope_id,code,name_vi,name_en,description,active,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, body.getCarrierId());
            query.setLong(2, scopeId);
            query.setString(3, body.getCode());
            query.setString(4, body.getNameVi().strip());
            query.setString(5, body.getNameEn().strip());
            query.setString(6, body.getDescription().strip());
            query.setBoolean(7, body.isActive());
            query.setLong(8, actor);
            query.setLong(9, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    /** CAS không tự lấy version mới để ghi đè; carrier/code/scope giữ nguyên. */
    public void update(Connection connection, long id, RouteWriteRequest body, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carrier_routes SET name_vi=?,name_en=?,description=?,active=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND code=? AND version=?"
            )
        ) {
            query.setString(1, body.getNameVi().strip());
            query.setString(2, body.getNameEn().strip());
            query.setString(3, body.getDescription().strip());
            query.setBoolean(4, body.isActive());
            query.setLong(5, actor);
            query.setLong(6, id);
            query.setLong(7, body.getCarrierId());
            query.setString(8, body.getCode());
            query.setLong(9, body.getVersion());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Thay mẫu điểm nguyên tử; chuyến dùng snapshot độc lập nên không tham chiếu ID của mẫu điểm. */
    public void replaceStops(Connection connection, long id, long scopeId, RouteWriteRequest body, long actor)
        throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM carrier_route_stops WHERE route_id=?")) {
            query.setLong(1, id);
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carrier_route_stops(route_id,carrier_id,scope_id,location_id,stop_order,arrival_offset_minutes,departure_offset_minutes,cumulative_fare,pickup_allowed,dropoff_allowed,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
            )
        ) {
            int order = 0;
            for (var stop : body.getStops()) {
                query.setLong(1, id);
                query.setLong(2, body.getCarrierId());
                query.setLong(3, scopeId);
                query.setLong(4, stop.locationId());
                query.setInt(5, ++order);
                query.setInt(6, stop.arrivalOffsetMinutes());
                query.setInt(7, stop.departureOffsetMinutes());
                query.setBigDecimal(8, stop.cumulativeFare());
                query.setBoolean(9, stop.pickupAllowed());
                query.setBoolean(10, stop.dropoffAllowed());
                query.setLong(11, actor);
                query.setLong(12, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    private RouteResponse map(ResultSet row, List<RouteStopResponse> stops) throws SQLException {
        return new RouteResponse(
            row.getLong("id"),
            row.getLong("carrier_id"),
            row.getLong("scope_id"),
            row.getString("carrier_name"),
            row.getString("code"),
            row.getString("name_vi"),
            row.getString("name_en"),
            row.getString("description"),
            row.getBoolean("active"),
            row.getInt("stop_count"),
            row.getLong("version"),
            row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("created_by"),
            row.getLong("updated_by"),
            row.getBoolean("can_update"),
            stops
        );
    }
}
