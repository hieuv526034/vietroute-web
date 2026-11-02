package vn.vietroute.business.carrier.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.request.DriverRequest;
import vn.vietroute.business.carrier.dto.request.VehicleRequest;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Repo nhà xe/xe/tài xế: SELECT kiểm ID + scope trước CRUD JPA, không dùng companyId client làm quyền. */
@Repository
public class CarrierRepo extends JpaRepositorySupport<Carrier, Long> {

    private enum Kind {
        CARRIER(
            "carriers owner",
            "owner",
            """
            owner.id, owner.scope_id, owner.code, owner.name, owner.phone, owner.description, owner.active,
                owner.created_at, owner.updated_at, owner.created_by, owner.updated_by,
                owner.version
            """,
            "owner.code,owner.name,owner.phone"
        ),
        VEHICLE(
            """
            carrier_vehicles item
            JOIN carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            JOIN catalog_items vt ON vt.id = item.vehicle_type_id
            """,
            "item",
            """
            item.id, item.scope_id, item.carrier_id, owner.name AS carrier_name, item.code, item.plate_number,
                item.vehicle_type_id, vt.name_vi AS vehicle_type_name, item.capacity, item.active,
                item.created_at, item.updated_at, item.created_by, item.updated_by, item.version
            """,
            "item.code,item.plate_number,owner.name"
        ),
        DRIVER(
            """
            carrier_drivers item
            JOIN carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            """,
            "item",
            """
            item.id, item.scope_id, item.carrier_id, owner.name AS carrier_name, item.code, item.name, item.phone,
                item.user_id, item.active, item.created_at, item.updated_at, item.created_by, item.updated_by,
                item.version
            """,
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

    private final UserRoleRepo scopes;

    public CarrierRepo(EntityManager manager, JdbcTransactions transactions, UserRoleRepo scopes) {
        super(Carrier.class, manager, transactions);
        this.scopes = scopes;
    }

    /** Phân trang cùng predicate scope ở count và dữ liệu; không lọc trong RAM sau khi lấy trang. */
    public PageResponse<Carrier> carriers(CarrierSearch search) {
        return read(connection -> page(connection, Kind.CARRIER, search, CarrierRepo::carrier));
    }

    /** Picker cho CREATE dùng đúng quyền scoped CREATE của tài nguyên con, không lộ hồ sơ đầy đủ của nhà xe. */
    public PageResponse<Carrier> choices(CarrierSearch search) {
        return read(connection -> page(connection, Kind.CARRIER, search, CarrierRepo::carrier));
    }

    public PageResponse<Vehicle> vehicles(CarrierSearch search) {
        return read(connection -> page(connection, Kind.VEHICLE, search, CarrierRepo::vehicle));
    }

    public PageResponse<Driver> drivers(CarrierSearch search) {
        return read(connection -> page(connection, Kind.DRIVER, search, CarrierRepo::driver));
    }

    /** Resource/action là metadata từ service server; scope luôn đọc từ owner trong database. */
    public Carrier carrier(Connection connection, long id, boolean lock) throws SQLException {
        return find(connection, Kind.CARRIER, id, lock, CarrierRepo::carrier);
    }

    public Vehicle vehicle(Connection connection, long id, boolean lock) throws SQLException {
        return find(connection, Kind.VEHICLE, id, lock, CarrierRepo::vehicle);
    }

    public Driver driver(Connection connection, long id, boolean lock) throws SQLException {
        return find(connection, Kind.DRIVER, id, lock, CarrierRepo::driver);
    }

    /** Cờ UI chỉ là giao của quyền scoped UPDATE; capabilities/policy chung vẫn bắt buộc trên trình duyệt/API. */
    private static String columns(Kind kind) {
        return (
            kind.columns +
            ",(" +
            UserRoleRepo.visibleSql() +
            ") AS can_update" +
            (kind == Kind.DRIVER ? ",(" + UserRoleRepo.visibleSql() + ") AS can_assign" : "")
        );
    }

    private static int bindColumns(PreparedStatement query, Kind kind) throws SQLException {
        String path = switch (kind) {
            case CARRIER -> Routes.ADMIN_CARRIERS;
            case VEHICLE -> Routes.ADMIN_VEHICLES;
            case DRIVER -> Routes.ADMIN_DRIVERS;
        };
        int next = UserRoleRepo.bindVisible(query, 1, "PUT " + path + Routes.BY_ID);
        return kind == Kind.DRIVER
            ? UserRoleRepo.bindVisible(query, next, "POST " + path + Routes.DRIVER_ACCOUNT)
            : next;
    }

    private <T> T find(Connection connection, Kind kind, long id, boolean lock, RowMapper<T> mapper)
        throws SQLException {
        String sql = """
        SELECT %s
        FROM %s
        WHERE %s.id = ?
            AND %s
        %s
        """.formatted(
            columns(kind),
            kind.from,
            kind.alias,
            UserRoleRepo.visibleSql(),
            lock ? " FOR UPDATE OF " + kind.alias : ""
        );
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            int index = bindColumns(query, kind);
            query.setLong(index++, id);
            UserRoleRepo.bindVisible(query, index);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return mapper.map(row);
            }
        }
    }

    private <T> PageResponse<T> page(Connection connection, Kind kind, CarrierSearch page, RowMapper<T> mapper)
        throws SQLException {
        String searchText =
            kind == Kind.CARRIER
                ? "lower(owner.code || ' ' || owner.name || ' ' || owner.phone)"
                : "lower(concat_ws(' '," + kind.search + "))";
        String filter = """
        FROM %s
        WHERE %s
            AND (?::boolean IS NULL OR %s.active = ?)
            AND (?::bigint IS NULL OR owner.id = ?)
            AND %s LIKE ? ESCAPE '\\'
        """.formatted(kind.from, UserRoleRepo.visibleSql(), kind.alias, searchText);
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
            bindSearch(query, 1, page);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<T> content = new ArrayList<T>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s
                %s
                ORDER BY %s.id DESC
                LIMIT ? OFFSET ?
                """.formatted(columns(kind), filter, kind.alias)
            )
        ) {
            int index = bindColumns(query, kind);
            index = bindSearch(query, index, page);
            query.setInt(index++, page.getSize());
            query.setLong(index, page.offset());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) content.add(mapper.map(rows));
            }
        }
        return PageResponse.of(content, page, total);
    }

    private int bindSearch(PreparedStatement query, int start, CarrierSearch page) throws SQLException {
        int index = UserRoleRepo.bindVisible(query, start);
        query.setObject(index++, page.getActive(), Types.BOOLEAN);
        query.setObject(index++, page.getActive(), Types.BOOLEAN);
        query.setObject(index++, page.getCarrierId(), Types.BIGINT);
        query.setObject(index++, page.getCarrierId(), Types.BIGINT);
        String search = page.getQuery() == null ? "" : page.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setString(index++, "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return index;
    }

    /** Tạo scope và nhà xe cùng transaction; lỗi UNIQUE rollback cả scope, không để scope mồ côi. */
    public long insertCarrier(Connection connection, CarrierRequest body, long actor) throws SQLException {
        long scope = scopes.create(connection, body.getName(), actor);
        Carrier value = Carrier.builder()
            .scopeId(scope)
            .code(body.getCode())
            .name(body.getName().strip())
            .phone(body.getPhone().strip())
            .description(body.getDescription().strip())
            .active(body.isActive())
            .scopeCode("CARRIER_" + scope)
            .scopeNameVi(body.getName().strip())
            .scopeNameEn(body.getName().strip())
            .scopeActive(true)
            .scopeVersion(0)
            .build();
        return created(value, actor).getId();
    }

    /** Carrier ID/scope/code bất biến; compare-and-set version là điểm chốt ghi cạnh tranh. */
    public void updateCarrier(Connection connection, Carrier current, CarrierRequest body, long actor) {
        // Request dùng chung cho tạo/sửa; mã được gửi lại phải trùng định danh đã lưu.
        BusinessRules.require(
            body.getCode() == null || current.getCode().equals(body.getCode()),
            ErrorCode.INVALID_REQUEST
        );
        Carrier value = locked(Carrier.class, current.getId(), body.getVersion());
        BusinessRules.require(value.getScopeId() == current.getScopeId(), ErrorCode.STALE_VERSION);
        value.setName(body.getName().strip());
        value.setPhone(body.getPhone().strip());
        value.setDescription(body.getDescription().strip());
        value.setActive(body.isActive());
        changed(value, actor);
    }

    /** Loại xe active được khóa trong transaction; FK ghép cũng chặn nhầm BOOKING_SOURCE. */
    public void requireVehicleType(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM catalog_items WHERE id=? AND type='VEHICLE_TYPE' AND active FOR SHARE"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /** Account không tự được cấp role: chỉ liên kết DRIVER đã có role global và scope đúng nhà xe. */
    public void requireDriverUser(Connection connection, long id, long scope) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT u.id
                FROM auth_users u
                JOIN auth_user_roles ur ON ur.user_id = u.id
                JOIN auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
                JOIN (
                    SELECT scope_id, user_id, bool_and(active) AS active, max(version) AS version
                    FROM auth_user_roles
                    WHERE scope_id > 0
                    GROUP BY scope_id, user_id
                ) sm ON sm.user_id = u.id
                    AND sm.scope_id = ?
                    AND sm.active
                JOIN (
                    SELECT scope_id, user_id, role_code
                    FROM auth_user_roles
                    WHERE scope_id > 0 AND active
                ) sr ON sr.scope_id = sm.scope_id
                    AND sr.user_id = sm.user_id
                    AND sr.role_code = ur.role_code
                WHERE u.id = ? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password
                    AND u.email_verified_at IS NOT NULL
                FOR SHARE OF u
                """
            )
        ) {
            query.setLong(1, scope);
            query.setLong(2, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /** Caller giữ khóa cha trước hồ sơ như luồng tạo/phân công chuyến, tránh thay identity khi đã có nhiệm vụ. */
    public void requireDriverUnassigned(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT 1
                FROM carrier_trips
                WHERE driver_id = ?
                    AND status IN ('DRAFT', 'OPEN', 'IN_PROGRESS')
                LIMIT 1
                """
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
        }
    }

    /** Hồ sơ cấp account mới tạm ngừng để hoàn tất đổi mật khẩu/email; không thay quy tắc account ready. */
    public void linkProvisionedDriver(Connection connection, Driver current, long user, long actor)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE carrier_drivers
                SET user_id = ?, active = false, updated_by = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = ? AND carrier_id = ? AND scope_id = ? AND user_id IS NULL AND version = ?
                """
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

    public long insertVehicle(Connection connection, Carrier parent, VehicleRequest body, long actor) {
        Vehicle value = Vehicle.builder()
            .carrierId(parent.getId())
            .scopeId(parent.getScopeId())
            .code(body.getCode())
            .plateNumber(body.getPlateNumber().strip().toUpperCase(Locale.ROOT))
            .vehicleTypeId(body.getVehicleTypeId())
            .vehicleType("VEHICLE_TYPE")
            .capacity(body.getCapacity())
            .active(body.isActive())
            .build();
        return created(value, actor).getId();
    }

    public void updateVehicle(Connection connection, Vehicle current, VehicleRequest body, long actor) {
        BusinessRules.require(
            (body.getCode() == null || current.getCode().equals(body.getCode())) &&
                (body.getCarrierId() == null || current.getCarrierId() == body.getCarrierId()),
            ErrorCode.INVALID_REQUEST
        );
        Vehicle value = locked(Vehicle.class, current.getId(), body.getVersion());
        BusinessRules.require(
            value.getCarrierId() == current.getCarrierId() && value.getScopeId() == current.getScopeId(),
            ErrorCode.STALE_VERSION
        );
        value.setPlateNumber(body.getPlateNumber().strip().toUpperCase(Locale.ROOT));
        value.setVehicleTypeId(body.getVehicleTypeId());
        value.setCapacity(body.getCapacity());
        value.setActive(body.isActive());
        changed(value, actor);
    }

    public long insertDriver(Connection connection, Carrier parent, DriverRequest body, long actor) {
        Driver value = Driver.builder()
            .carrierId(parent.getId())
            .scopeId(parent.getScopeId())
            .code(body.getCode())
            .name(body.getName().strip())
            .phone(body.getPhone().strip())
            .userId(body.getUserId())
            .active(body.isActive())
            .build();
        return created(value, actor).getId();
    }

    public void updateDriver(Connection connection, Driver current, DriverRequest body, long actor) {
        BusinessRules.require(
            (body.getCode() == null || current.getCode().equals(body.getCode())) &&
                (body.getCarrierId() == null || current.getCarrierId() == body.getCarrierId()),
            ErrorCode.INVALID_REQUEST
        );
        Driver value = locked(Driver.class, current.getId(), body.getVersion());
        BusinessRules.require(
            value.getCarrierId() == current.getCarrierId() && value.getScopeId() == current.getScopeId(),
            ErrorCode.STALE_VERSION
        );
        value.setName(body.getName().strip());
        value.setPhone(body.getPhone().strip());
        value.setUserId(body.getUserId());
        value.setActive(body.isActive());
        changed(value, actor);
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

    /** Timestamp/actor chỉ lấy từ server, không từ request; caller đang sở hữu transaction JPA/JDBC. */
    private <E extends BaseEntity> E created(E value, long actor) {
        Instant now = Instant.now();
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setCreatedBy(actor);
        value.setUpdatedBy(actor);
        entityManager.persist(value);
        entityManager.flush();
        return value;
    }

    private void changed(BaseEntity value, long actor) {
        value.setUpdatedAt(Instant.now());
        value.setUpdatedBy(actor);
        entityManager.flush();
    }

    /** SELECT ID/scope đã được guard trên connection caller; khóa ORM giữ version đúng khi ghi. */
    private <E extends BaseEntity> E locked(Class<E> model, long id, long version) {
        E value = BusinessRules.requireFound(entityManager.find(model, id, LockModeType.PESSIMISTIC_WRITE));
        BusinessRules.requireVersion(version, value.getVersion());
        return value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Published {

        private long id;

        private String code;

        private String name;

        private String phone;

        private String description;
    }

    private static final String FROM = """
    FROM carriers c
    JOIN (
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM carriers
    ) s ON s.id = c.scope_id
    WHERE c.active AND s.active
    """;
    private static final String COLUMNS = "c.id,c.code,c.name,c.phone,c.description";

    /** Từ khóa nguyên văn và phân trang ổn định name/id được xử lý hoàn toàn trong PostgreSQL. */
    public PageResponse<Published> page(Connection connection, PublicCarrierSearch body) throws SQLException {
        String where = FROM + " AND lower(c.code || ' ' || c.name || ' ' || c.phone) LIKE ? ESCAPE '!'";
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        // LIKE tương thích index trigram; escape để %, _ và ! vẫn là ký tự nguyên văn như strpos trước đây.
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + where)) {
            query.setString(1, pattern);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<Published> result = new ArrayList<Published>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s
                %s
                ORDER BY c.name, c.id
                LIMIT ? OFFSET ?
                """.formatted(COLUMNS, where)
            )
        ) {
            query.setString(1, pattern);
            query.setInt(2, body.getSize());
            query.setLong(3, body.offset());
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) result.add(map(row));
            }
        }
        return PageResponse.of(result, body, total);
    }

    /** Nhà xe/scope ngừng công bố trả 404; không dùng membership để lọc mất lựa chọn công khai. */
    public Published find(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT %s
                %s
                    AND c.id = ?
                """.formatted(COLUMNS, FROM)
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(row);
            }
        }
    }

    private Published map(ResultSet row) throws SQLException {
        return new Published(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5));
    }
}
