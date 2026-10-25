package vn.vietroute.business.trip.repository;

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
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.trip.dto.request.TripChoiceRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.validation.BusinessRules;

/** Dữ liệu nguồn cho chuyến; luôn dùng TRIP action/scope thay vì quyền CRUD của tài nguyên phụ thuộc. */
@Repository
public class TripTemplateRepository extends BaseRepository {

    public record Template(
        long id,
        long carrierId,
        long scopeId,
        String nameVi,
        String nameEn,
        boolean active,
        List<SourceStop> stops
    ) {}

    public record SourceStop(
        long locationId,
        long provinceId,
        int order,
        String nameVi,
        String nameEn,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        int arrival,
        int departure,
        BigDecimal fare,
        boolean pickup,
        boolean dropoff,
        boolean active
    ) {}

    public record Assets(
        long vehicleId,
        long driverId,
        long driverUserId,
        String plate,
        long typeId,
        String typeVi,
        String typeEn,
        int capacity,
        String driverName,
        String driverPhone
    ) {}

    public TripTemplateRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Mẫu chỉ đọc sau khi service xác định nhà xe bằng guard; khóa tuyến ngăn thay điểm trong lúc chụp. */
    public Template template(Connection connection, long routeId, Long carrierId, boolean lock) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT id,carrier_id,scope_id,name_vi,name_en,active FROM carrier_routes WHERE id=? AND (?::bigint IS NULL OR carrier_id=?)" +
                    (lock ? " FOR SHARE" : "")
            )
        ) {
            query.setLong(1, routeId);
            query.setObject(2, carrierId);
            query.setObject(3, carrierId);
            try (var row = query.executeQuery()) {
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
        var result = new ArrayList<SourceStop>();
        try (
            var query = connection.prepareStatement(
                "SELECT l.id,l.province_id,s.stop_order,l.name_vi,l.name_en,l.address,l.latitude,l.longitude,s.arrival_offset_minutes,s.departure_offset_minutes,s.cumulative_fare,s.pickup_allowed,s.dropoff_allowed,l.active AND p.active AS usable FROM carrier_route_stops s JOIN location_places l ON l.id=s.location_id JOIN location_provinces p ON p.id=l.province_id WHERE s.route_id=? ORDER BY s.stop_order FOR SHARE OF l,p"
            )
        ) {
            query.setLong(1, routeId);
            try (var row = query.executeQuery()) {
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
            var query = connection.prepareStatement(
                "SELECT v.plate_number,v.vehicle_type_id,t.name_vi,t.name_en,v.capacity FROM carrier_vehicles v JOIN catalog_items t ON t.id=v.vehicle_type_id AND t.type='VEHICLE_TYPE' AND t.active WHERE v.id=? AND v.carrier_id=? AND v.scope_id=? AND v.active FOR UPDATE OF v FOR SHARE OF t"
            )
        ) {
            query.setLong(1, vehicleId);
            query.setLong(2, carrierId);
            query.setLong(3, scopeId);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.TRIP_INVALID);
                plate = row.getString(1);
                typeId = row.getLong(2);
                typeVi = row.getString(3);
                typeEn = row.getString(4);
                capacity = row.getInt(5);
            }
        }
        try (
            var query = connection.prepareStatement(
                "SELECT user_id,name,phone FROM carrier_drivers WHERE id=? AND carrier_id=? AND scope_id=? AND active AND user_id IS NOT NULL FOR UPDATE"
            )
        ) {
            query.setLong(1, driverId);
            query.setLong(2, carrierId);
            query.setLong(3, scopeId);
            try (var row = query.executeQuery()) {
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
            var query = connection.prepareStatement(
                "SELECT 1 FROM carrier_trips WHERE id<>? AND status IN('OPEN','IN_PROGRESS') AND (vehicle_id=? OR driver_id=? OR driver_user_id=?) AND ((departure_at<? AND end_at>?) OR (? AND status='IN_PROGRESS')) LIMIT 1"
            )
        ) {
            query.setLong(1, exceptId);
            query.setLong(2, assets.vehicleId());
            query.setLong(3, assets.driverId());
            query.setLong(4, assets.driverUserId());
            query.setTimestamp(5, Timestamp.from(end));
            query.setTimestamp(6, Timestamp.from(start));
            query.setBoolean(7, starting);
            try (var row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.TRIP_SCHEDULE_CONFLICT);
            }
        }
    }

    /** Đếm và trang dùng một predicate scope; chỉ projection tối thiểu cho picker. */
    public PageResponse<TripChoiceResponse> choices(
        Connection connection,
        TripChoiceRequest body,
        PermissionAction action
    ) throws SQLException {
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
                from =
                    "carrier_routes item JOIN carriers owner ON owner.id=item.carrier_id AND owner.scope_id=item.scope_id";
                id = "item.id";
                code = "item.code";
                name = "concat_ws(' / ',item.name_vi,item.name_en)";
                usable = "owner.active AND item.active";
            }
            case VEHICLE -> {
                from =
                    "carrier_vehicles item JOIN carriers owner ON owner.id=item.carrier_id AND owner.scope_id=item.scope_id JOIN catalog_items vt ON vt.id=item.vehicle_type_id";
                id = "item.id";
                code = "item.code";
                name = "item.plate_number";
                capacity = "item.capacity";
                usable = "owner.active AND item.active AND vt.active AND vt.type='VEHICLE_TYPE'";
            }
            case DRIVER -> {
                from =
                    "carrier_drivers item JOIN carriers owner ON owner.id=item.carrier_id AND owner.scope_id=item.scope_id JOIN auth_users du ON du.id=item.user_id";
                id = "item.id";
                code = "item.code";
                name = "item.name";
                usable =
                    "owner.active AND item.active AND du.active AND NOT du.activation_pending AND NOT du.must_change_password AND du.email_verified_at IS NOT NULL AND EXISTS(SELECT 1 FROM auth_scope_memberships sm JOIN auth_scope_roles sr ON sr.scope_id=sm.scope_id AND sr.user_id=sm.user_id JOIN auth_user_roles ur ON ur.user_id=sr.user_id AND ur.role_code=sr.role_code JOIN auth_roles rr ON rr.code=ur.role_code AND rr.active AND NOT rr.deleted WHERE sm.scope_id=owner.scope_id AND sm.user_id=du.id AND sm.active AND rr.code='DRIVER')";
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        BusinessRules.require(
            body.getKind() == TripChoiceRequest.Kind.CARRIER || body.getCarrierId() != null,
            ErrorCode.INVALID_REQUEST
        );
        String filter =
            " FROM " +
            from +
            " WHERE " +
            DataScopeGuard.visibleSql() +
            " AND " +
            usable +
            " AND (?::bigint IS NULL OR owner.id=?) AND strpos(lower(concat_ws(' '," +
            code +
            "," +
            name +
            ")),?)>0";
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
            bindChoice(query, body, action);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var result = new ArrayList<TripChoiceResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT " +
                    id +
                    "," +
                    code +
                    "," +
                    name +
                    "," +
                    capacity +
                    filter +
                    " ORDER BY " +
                    id +
                    " LIMIT ? OFFSET ?"
            )
        ) {
            int at = bindChoice(query, body, action);
            query.setInt(at++, body.getSize());
            query.setLong(at, body.offset());
            try (var row = query.executeQuery()) {
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

    private int bindChoice(PreparedStatement query, TripChoiceRequest body, PermissionAction action)
        throws SQLException {
        int at = DataScopeGuard.bindVisible(query, 1, Groups.TRIP, action);
        query.setObject(at++, body.getCarrierId());
        query.setObject(at++, body.getCarrierId());
        query.setString(at++, body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT));
        return at;
    }
}
