package vn.vietroute.business.carrier.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.repository.BusinessScopeRepository;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.carrier.dto.request.CarrierCreateRequest;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.CarrierUpdateRequest;
import vn.vietroute.business.carrier.dto.request.DriverCreateRequest;
import vn.vietroute.business.carrier.dto.request.DriverUpdateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleCreateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleUpdateRequest;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** JDBC nhà xe: ID + scope được lọc ngay trong SELECT/UPDATE, không dùng companyId client làm quyền. */
@Repository
public class CarrierRepository extends BaseRepository {

    private enum Kind {
        CARRIER(
            "carriers owner",
            "owner",
            "owner.id,owner.scope_id,owner.code,owner.name,owner.phone,owner.description,owner.active,owner.logo_media_id,owner.created_at,owner.updated_at,owner.created_by,owner.updated_by,owner.version",
            "owner.code,owner.name,owner.phone"
        ),
        VEHICLE(
            "carrier_vehicles item JOIN carriers owner ON owner.id=item.carrier_id AND owner.scope_id=item.scope_id JOIN catalog_items vt ON vt.id=item.vehicle_type_id",
            "item",
            "item.id,item.scope_id,item.carrier_id,owner.name AS carrier_name,item.code,item.plate_number,item.vehicle_type_id,vt.name_vi AS vehicle_type_name,item.capacity,item.active,item.photo_media_id,item.created_at,item.updated_at,item.created_by,item.updated_by,item.version",
            "item.code,item.plate_number,owner.name"
        ),
        DRIVER(
            "carrier_drivers item JOIN carriers owner ON owner.id=item.carrier_id AND owner.scope_id=item.scope_id",
            "item",
            "item.id,item.scope_id,item.carrier_id,owner.name AS carrier_name,item.code,item.name,item.phone,item.user_id,item.active,item.created_at,item.updated_at,item.created_by,item.updated_by,item.version",
            "item.code,item.name,item.phone,owner.name"
        );

        private final String from;
        private final String alias;
        private final String columns;
        private final String search;

        Kind(String from, String alias, String columns, String search) {
            this.from = from;
            this.alias = alias;
            this.columns = columns;
            this.search = search;
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    private final BusinessScopeRepository scopes;

    public CarrierRepository(JdbcConnectionFactory connections, BusinessScopeRepository scopes) {
        super(connections);
        this.scopes = scopes;
    }

    /** Phân trang cùng predicate scope ở count và dữ liệu; không lọc trong RAM sau khi lấy trang. */
    public PageResponse<Carrier> carriers(CarrierSearchRequest search) {
        return read(connection -> page(connection, Kind.CARRIER, search, Groups.CARRIER, CarrierRepository::carrier));
    }

    /** Picker cho CREATE dùng đúng quyền scoped CREATE của tài nguyên con, không lộ hồ sơ đầy đủ của nhà xe. */
    public PageResponse<Carrier> choices(CarrierSearchRequest search, String resource) {
        return read(connection ->
            page(connection, Kind.CARRIER, search, resource, PermissionAction.CREATE, CarrierRepository::carrier)
        );
    }

    public PageResponse<Vehicle> vehicles(CarrierSearchRequest search) {
        return read(connection -> page(connection, Kind.VEHICLE, search, Groups.VEHICLE, CarrierRepository::vehicle));
    }

    public PageResponse<Driver> drivers(CarrierSearchRequest search) {
        return read(connection -> page(connection, Kind.DRIVER, search, Groups.DRIVER, CarrierRepository::driver));
    }

    /** Resource/action là metadata từ service server; scope luôn đọc từ owner trong database. */
    public Carrier carrier(Connection connection, long id, String resource, PermissionAction action, boolean lock)
        throws SQLException {
        return find(connection, Kind.CARRIER, id, resource, action, lock, CarrierRepository::carrier);
    }

    public Vehicle vehicle(Connection connection, long id, PermissionAction action, boolean lock) throws SQLException {
        return find(connection, Kind.VEHICLE, id, Groups.VEHICLE, action, lock, CarrierRepository::vehicle);
    }

    public Driver driver(Connection connection, long id, PermissionAction action, boolean lock) throws SQLException {
        return find(connection, Kind.DRIVER, id, Groups.DRIVER, action, lock, CarrierRepository::driver);
    }

    /** Cờ UI chỉ là giao của quyền scoped UPDATE; capabilities/policy chung vẫn bắt buộc trên trình duyệt/API. */
    private static String columns(Kind kind) {
        return (
            kind.columns +
            ",(" +
            DataScopeGuard.visibleSql() +
            ") AS can_update" +
            (kind == Kind.DRIVER ? ",(" + DataScopeGuard.visibleSql() + ") AS can_assign" : "")
        );
    }

    private static int bindColumns(PreparedStatement query, Kind kind, String resource) throws SQLException {
        int next = DataScopeGuard.bindVisible(query, 1, resource, PermissionAction.UPDATE);
        return kind == Kind.DRIVER
            ? DataScopeGuard.bindVisible(query, next, Groups.DRIVER, PermissionAction.ASSIGN)
            : next;
    }

    private <T> T find(
        Connection connection,
        Kind kind,
        long id,
        String resource,
        PermissionAction action,
        boolean lock,
        RowMapper<T> mapper
    ) throws SQLException {
        String sql =
            "SELECT " +
            columns(kind) +
            " FROM " +
            kind.from +
            " WHERE " +
            kind.alias +
            ".id=? AND " +
            DataScopeGuard.visibleSql() +
            (lock ? " FOR UPDATE OF " + kind.alias : "");
        try (var query = connection.prepareStatement(sql)) {
            int index = bindColumns(query, kind, resource);
            query.setLong(index++, id);
            DataScopeGuard.bindVisible(query, index, resource, action);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return mapper.map(row);
            }
        }
    }

    private <T> PageResponse<T> page(
        Connection connection,
        Kind kind,
        CarrierSearchRequest page,
        String resource,
        RowMapper<T> mapper
    ) throws SQLException {
        return page(connection, kind, page, resource, PermissionAction.READ, mapper);
    }

    private <T> PageResponse<T> page(
        Connection connection,
        Kind kind,
        CarrierSearchRequest page,
        String resource,
        PermissionAction action,
        RowMapper<T> mapper
    ) throws SQLException {
        String searchText =
            kind == Kind.CARRIER
                ? "lower(owner.code || ' ' || owner.name || ' ' || owner.phone)"
                : "lower(concat_ws(' '," + kind.search + "))";
        String filter =
            " FROM " +
            kind.from +
            " WHERE " +
            DataScopeGuard.visibleSql() +
            " AND (?::boolean IS NULL OR " +
            kind.alias +
            ".active=?) AND (?::bigint IS NULL OR owner.id=?) AND " +
            searchText +
            " LIKE ? ESCAPE '\\'";
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
            bindSearch(query, 1, page, resource, action);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var content = new ArrayList<T>();
        try (
            var query = connection.prepareStatement(
                "SELECT " + columns(kind) + filter + " ORDER BY " + kind.alias + ".id DESC LIMIT ? OFFSET ?"
            )
        ) {
            int index = bindColumns(query, kind, resource);
            index = bindSearch(query, index, page, resource, action);
            query.setInt(index++, page.getSize());
            query.setLong(index, page.offset());
            try (var rows = query.executeQuery()) {
                while (rows.next()) content.add(mapper.map(rows));
            }
        }
        return PageResponse.of(content, page, total);
    }

    private int bindSearch(
        PreparedStatement query,
        int start,
        CarrierSearchRequest page,
        String resource,
        PermissionAction action
    ) throws SQLException {
        int index = DataScopeGuard.bindVisible(query, start, resource, action);
        query.setObject(index++, page.getActive(), Types.BOOLEAN);
        query.setObject(index++, page.getActive(), Types.BOOLEAN);
        query.setObject(index++, page.getCarrierId(), Types.BIGINT);
        query.setObject(index++, page.getCarrierId(), Types.BIGINT);
        String search = page.getQuery() == null ? "" : page.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setString(index++, "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return index;
    }

    /** Tạo scope và nhà xe cùng transaction; lỗi UNIQUE rollback cả scope, không để scope mồ côi. */
    public long insertCarrier(Connection connection, CarrierCreateRequest body, long actor) throws SQLException {
        long scope = scopes.create(connection, body.getName(), actor);
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carriers(scope_id,code,name,phone,description,active,logo_media_id,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, scope);
            query.setString(2, body.getCode());
            query.setString(3, body.getName().strip());
            query.setString(4, body.getPhone().strip());
            query.setString(5, body.getDescription().strip());
            query.setBoolean(6, body.isActive());
            query.setObject(7, body.getLogoMediaId(), Types.BIGINT);
            query.setLong(8, actor);
            query.setLong(9, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    /** Carrier ID/scope/code bất biến; compare-and-set version là điểm chốt ghi cạnh tranh. */
    public void updateCarrier(Connection connection, Carrier current, CarrierUpdateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carriers SET name=?,phone=?,description=?,active=?,logo_media_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setString(1, body.getName().strip());
            query.setString(2, body.getPhone().strip());
            query.setString(3, body.getDescription().strip());
            query.setBoolean(4, body.isActive());
            query.setObject(5, body.getLogoMediaId(), Types.BIGINT);
            query.setLong(6, actor);
            query.setLong(7, current.getId());
            query.setLong(8, current.getScopeId());
            query.setLong(9, body.getVersion());
            updated(query);
        }
    }

    /** Loại xe active được khóa trong transaction; FK ghép cũng chặn nhầm BOOKING_SOURCE. */
    public void requireVehicleType(Connection connection, long id) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT id FROM catalog_items WHERE id=? AND type='VEHICLE_TYPE' AND active FOR SHARE"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /** Account không tự được cấp role: chỉ liên kết DRIVER đã có role global và scope đúng nhà xe. */
    public void requireDriverUser(Connection connection, long id, long scope) throws SQLException {
        try (
            var query = connection.prepareStatement(
                """
                SELECT u.id FROM auth_users u
                JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.role_code='DRIVER'
                JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
                JOIN auth_scope_memberships sm ON sm.user_id=u.id AND sm.scope_id=? AND sm.active
                JOIN auth_scope_roles sr ON sr.scope_id=sm.scope_id AND sr.user_id=sm.user_id AND sr.role_code=ur.role_code
                WHERE u.id=? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
                FOR SHARE OF u
                """
            )
        ) {
            query.setLong(1, scope);
            query.setLong(2, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /** Caller giữ khóa cha trước hồ sơ như luồng tạo/phân công chuyến, tránh thay identity khi đã có nhiệm vụ. */
    public void requireDriverUnassigned(Connection connection, long id) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT 1 FROM carrier_trips WHERE driver_id=? AND status IN('DRAFT','OPEN','IN_PROGRESS') LIMIT 1"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
        }
    }

    /** Hồ sơ cấp account mới tạm ngừng để hoàn tất đổi mật khẩu/email; không thay quy tắc account ready. */
    public void linkProvisionedDriver(Connection connection, Driver current, long user, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carrier_drivers SET user_id=?,active=false,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND scope_id=? AND user_id IS NULL AND version=?"
            )
        ) {
            query.setLong(1, user);
            query.setLong(2, actor);
            query.setLong(3, current.getId());
            query.setLong(4, current.getCarrierId());
            query.setLong(5, current.getScopeId());
            query.setLong(6, current.getVersion());
            updated(query);
        }
    }

    public long insertVehicle(Connection connection, Carrier parent, VehicleCreateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carrier_vehicles(carrier_id,scope_id,code,plate_number,vehicle_type_id,capacity,active,photo_media_id,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, parent.getId());
            query.setLong(2, parent.getScopeId());
            query.setString(3, body.getCode());
            query.setString(4, body.getPlateNumber().strip().toUpperCase(Locale.ROOT));
            query.setLong(5, body.getVehicleTypeId());
            query.setInt(6, body.getCapacity());
            query.setBoolean(7, body.isActive());
            query.setObject(8, body.getPhotoMediaId(), Types.BIGINT);
            query.setLong(9, actor);
            query.setLong(10, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    public void updateVehicle(Connection connection, Vehicle current, VehicleUpdateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carrier_vehicles SET plate_number=?,vehicle_type_id=?,capacity=?,active=?,photo_media_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setString(1, body.getPlateNumber().strip().toUpperCase(Locale.ROOT));
            query.setLong(2, body.getVehicleTypeId());
            query.setInt(3, body.getCapacity());
            query.setBoolean(4, body.isActive());
            query.setObject(5, body.getPhotoMediaId(), Types.BIGINT);
            query.setLong(6, actor);
            query.setLong(7, current.getId());
            query.setLong(8, current.getCarrierId());
            query.setLong(9, current.getScopeId());
            query.setLong(10, body.getVersion());
            updated(query);
        }
    }

    public long insertDriver(Connection connection, Carrier parent, DriverCreateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO carrier_drivers(carrier_id,scope_id,code,name,phone,user_id,active,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            query.setLong(1, parent.getId());
            query.setLong(2, parent.getScopeId());
            query.setString(3, body.getCode());
            query.setString(4, body.getName().strip());
            query.setString(5, body.getPhone().strip());
            query.setObject(6, body.getUserId(), Types.BIGINT);
            query.setBoolean(7, body.isActive());
            query.setLong(8, actor);
            query.setLong(9, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    public void updateDriver(Connection connection, Driver current, DriverUpdateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE carrier_drivers SET name=?,phone=?,user_id=?,active=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND carrier_id=? AND scope_id=? AND version=?"
            )
        ) {
            query.setString(1, body.getName().strip());
            query.setString(2, body.getPhone().strip());
            query.setObject(3, body.getUserId(), Types.BIGINT);
            query.setBoolean(4, body.isActive());
            query.setLong(5, actor);
            query.setLong(6, current.getId());
            query.setLong(7, current.getCarrierId());
            query.setLong(8, current.getScopeId());
            query.setLong(9, body.getVersion());
            updated(query);
        }
    }

    private static void updated(PreparedStatement query) throws SQLException {
        if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    private static <T extends BaseEntity> T audit(T item, ResultSet row) throws SQLException {
        item.setId(row.getLong("id"));
        item.setCreatedAt(row.getTimestamp("created_at").toInstant());
        item.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        item.setCreatedBy(row.getLong("created_by"));
        item.setUpdatedBy(row.getLong("updated_by"));
        item.setVersion(row.getLong("version"));
        return item;
    }

    private static Carrier carrier(ResultSet row) throws SQLException {
        return audit(
            Carrier.builder()
                .scopeId(row.getLong("scope_id"))
                .canUpdate(row.getBoolean("can_update"))
                .code(row.getString("code"))
                .name(row.getString("name"))
                .phone(row.getString("phone"))
                .description(row.getString("description"))
                .active(row.getBoolean("active"))
                .logoMediaId(row.getObject("logo_media_id", Long.class))
                .build(),
            row
        );
    }

    private static Vehicle vehicle(ResultSet row) throws SQLException {
        return audit(
            Vehicle.builder()
                .scopeId(row.getLong("scope_id"))
                .canUpdate(row.getBoolean("can_update"))
                .carrierId(row.getLong("carrier_id"))
                .carrierName(row.getString("carrier_name"))
                .code(row.getString("code"))
                .plateNumber(row.getString("plate_number"))
                .vehicleTypeId(row.getLong("vehicle_type_id"))
                .vehicleTypeName(row.getString("vehicle_type_name"))
                .capacity(row.getInt("capacity"))
                .active(row.getBoolean("active"))
                .photoMediaId(row.getObject("photo_media_id", Long.class))
                .build(),
            row
        );
    }

    private static Driver driver(ResultSet row) throws SQLException {
        return audit(
            Driver.builder()
                .scopeId(row.getLong("scope_id"))
                .canUpdate(row.getBoolean("can_update"))
                .canAssign(row.getBoolean("can_assign"))
                .carrierId(row.getLong("carrier_id"))
                .carrierName(row.getString("carrier_name"))
                .code(row.getString("code"))
                .name(row.getString("name"))
                .phone(row.getString("phone"))
                .userId(row.getObject("user_id", Long.class))
                .active(row.getBoolean("active"))
                .build(),
            row
        );
    }
}
