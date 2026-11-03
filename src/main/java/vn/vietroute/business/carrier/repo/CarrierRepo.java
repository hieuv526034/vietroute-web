package vn.vietroute.business.carrier.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.request.DriverRequest;
import vn.vietroute.business.carrier.dto.request.VehicleRequest;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.business.catalog.constant.CategoryRefType;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Repo nhà xe/xe/tài xế: SELECT kiểm ID + scope trước CRUD JPA, không dùng companyId client làm quyền. */
@Repository
public class CarrierRepo extends JpaRepositorySupport<Carrier, Long> {

    private enum Kind {
        CARRIER(
            "{h-schema}carriers owner",
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
            {h-schema}carrier_vehicles item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type='VEHICLE_TYPE' AND vt.type='VALUE'
            """,
            "item",
            """
            item.id, item.scope_id, item.carrier_id, owner.name AS carrier_name, item.code, item.plate_number,
                item.vehicle_type_id, vt.name AS vehicle_type_name, item.capacity, item.active,
                item.created_at, item.updated_at, item.created_by, item.updated_by, item.version
            """,
            "item.code,item.plate_number,owner.name"
        ),
        DRIVER(
            """
            {h-schema}carrier_drivers item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
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
        T map(Tuple row);
    }

    private final UserRoleRepo scopes;

    public CarrierRepo(EntityManager manager, UserRoleRepo scopes) {
        super(Carrier.class, manager);
        this.scopes = scopes;
    }

    /** Phân trang cùng predicate scope ở count và dữ liệu; không lọc trong RAM sau khi lấy trang. */
    @Transactional(readOnly = true)
    public PageResponse<Carrier> carriers(CarrierSearch search) {
        return page(Kind.CARRIER, search, CarrierRepo::carrier);
    }

    /** Picker cho CREATE dùng đúng quyền scoped CREATE của tài nguyên con, không lộ hồ sơ đầy đủ của nhà xe. */
    @Transactional(readOnly = true)
    public PageResponse<Carrier> choices(CarrierSearch search) {
        return page(Kind.CARRIER, search, CarrierRepo::carrier);
    }

    @Transactional(readOnly = true)
    public PageResponse<Vehicle> vehicles(CarrierSearch search) {
        return page(Kind.VEHICLE, search, CarrierRepo::vehicle);
    }

    @Transactional(readOnly = true)
    public PageResponse<Driver> drivers(CarrierSearch search) {
        return page(Kind.DRIVER, search, CarrierRepo::driver);
    }

    /** Resource/action là metadata từ service server; scope luôn đọc từ owner trong database. */
    public Carrier carrier(long id, boolean lock) {
        return find(Kind.CARRIER, id, lock, CarrierRepo::carrier);
    }

    public Vehicle vehicle(long id, boolean lock) {
        return find(Kind.VEHICLE, id, lock, CarrierRepo::vehicle);
    }

    public Driver driver(long id, boolean lock) {
        return find(Kind.DRIVER, id, lock, CarrierRepo::driver);
    }

    /** Cờ UI chỉ là giao của quyền scoped UPDATE; capabilities/policy chung vẫn bắt buộc trên trình duyệt/API. */
    private static String columns(Kind kind) {
        return (
            kind.columns +
            ",(" +
            UserRoleRepo.visibleSql("updateTarget") +
            ") AS can_update" +
            (kind == Kind.DRIVER ? ",(" + UserRoleRepo.visibleSql("assignTarget") + ") AS can_assign" : "")
        );
    }

    private static void bindColumns(Query query, Kind kind) {
        String path = switch (kind) {
            case CARRIER -> Routes.ADMIN_CARRIERS;
            case VEHICLE -> Routes.ADMIN_VEHICLES;
            case DRIVER -> Routes.ADMIN_DRIVERS;
        };
        UserRoleRepo.bindVisible(query, "PUT " + path + Routes.BY_ID);
        if (kind == Kind.DRIVER) UserRoleRepo.bindVisible(
            query,
            "POST " + path + Routes.DRIVER_ACCOUNT,
            "assignTarget"
        );
    }

    private <T> T find(Kind kind, long id, boolean lock, RowMapper<T> mapper) {
        String sql = """
        SELECT %s
        FROM %s
        WHERE %s.id = :id
            AND %s
        %s
        """.formatted(
            columns(kind),
            kind.from,
            kind.alias,
            UserRoleRepo.visibleSql(),
            lock ? " FOR UPDATE OF " + kind.alias : ""
        );
        {
            Query query = entityManager.createNativeQuery(sql, Tuple.class);

            bindColumns(query, kind);
            query.setParameter("id", id);
            UserRoleRepo.bindVisible(query);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return mapper.map(row);
            }
        }
    }

    private <T> PageResponse<T> page(Kind kind, CarrierSearch page, RowMapper<T> mapper) {
        String searchText =
            kind == Kind.CARRIER
                ? "LOWER(owner.code || ' ' || owner.name || ' ' || owner.phone)"
                : "LOWER(CONCAT_WS(' '," + kind.search + "))";
        String filter = """
        FROM %s
        WHERE %s
            AND (CAST(:active AS BOOLEAN) IS NULL OR %s.active = :active)
            AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            AND %s LIKE :keyword ESCAPE '\\'
        """.formatted(kind.from, UserRoleRepo.visibleSql(), kind.alias, searchText);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            bindSearch(query, page);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<T> content = new ArrayList<T>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                ORDER BY %s.id DESC
                LIMIT :pageSize OFFSET :offset
                """.formatted(columns(kind), filter, kind.alias),
                Tuple.class
            );

            bindColumns(query, kind);
            bindSearch(query, page);
            query.setParameter("pageSize", page.getSize());
            query.setParameter("offset", page.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    content.add(mapper.map(rows));
                }
            }
        }
        return PageResponse.of(content, page, total);
    }

    private void bindSearch(Query query, CarrierSearch page) {
        UserRoleRepo.bindVisible(query);
        query.setParameter("active", page.getActive());
        query.setParameter("carrierId", page.getCarrierId());
        String search = page.getQuery() == null ? "" : page.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setParameter("keyword", "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }

    /** Tạo scope và nhà xe cùng transaction; lỗi UNIQUE rollback cả scope, không để scope mồ côi. */
    public long insertCarrier(CarrierRequest body, long actor) {
        long scope = scopes.create(body.getName(), actor);
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
    public void updateCarrier(Carrier current, CarrierRequest body, long actor) {
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
    public void requireVehicleType(long id) {
        {
            Query query = entityManager.createNativeQuery(
                "SELECT id FROM {h-schema}category WHERE id=:id AND ref_type='VEHICLE_TYPE' AND type='VALUE' AND active FOR SHARE",
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
                Tuple row = (Tuple) rowValues.get(0);
            }
        }
    }

    /** Account không tự được cấp role: chỉ liên kết DRIVER đã có role global và scope đúng nhà xe. */
    public void requireDriverUser(long id, long scope) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT u.id
                FROM {h-schema}auth_users u
                JOIN {h-schema}auth_user_roles ur ON ur.user_id = u.id
                JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
                JOIN (
                    SELECT scope_id, user_id, BOOL_AND(active) AS active, MAX(version) AS version
                    FROM {h-schema}auth_user_roles
                    WHERE scope_id > 0
                    GROUP BY scope_id, user_id
                ) sm ON sm.user_id = u.id
                    AND sm.scope_id = :scope
                    AND sm.active
                JOIN (
                    SELECT scope_id, user_id, role_code
                    FROM {h-schema}auth_user_roles
                    WHERE scope_id > 0 AND active
                ) sr ON sr.scope_id = sm.scope_id
                    AND sr.user_id = sm.user_id
                    AND sr.role_code = ur.role_code
                WHERE u.id = :id AND u.active AND NOT u.activation_pending AND NOT u.must_change_password
                    AND u.email_verified_at IS NOT NULL
                FOR SHARE OF u
                """,
                Tuple.class
            );

            query.setParameter("scope", scope);
            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
                Tuple row = (Tuple) rowValues.get(0);
            }
        }
    }

    /** Caller giữ khóa cha trước hồ sơ như luồng tạo/phân công chuyến, tránh thay identity khi đã có nhiệm vụ. */
    public void requireDriverUnassigned(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT 1
                FROM {h-schema}carrier_trips
                WHERE driver_id = :id
                    AND status IN ('DRAFT', 'OPEN', 'IN_PROGRESS')
                LIMIT 1
                """,
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                if (row != null) throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
        }
    }

    /** Hồ sơ cấp account mới tạm ngừng để hoàn tất đổi mật khẩu/email; không thay quy tắc account ready. */
    public void linkProvisionedDriver(Driver current, long user, long actor) {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}carrier_drivers
                SET user_id = :user, active = FALSE, updated_by = :actor, updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :currentId AND carrier_id = :carrierId AND scope_id = :scopeId AND user_id IS NULL AND version = :currentVersion
                """
            );

            query.setParameter("user", user);
            query.setParameter("actor", actor);
            query.setParameter("currentId", current.getId());
            query.setParameter("carrierId", current.getCarrierId());
            query.setParameter("scopeId", current.getScopeId());
            query.setParameter("currentVersion", current.getVersion());
            updated(query);
        }
    }

    public long insertVehicle(Carrier parent, VehicleRequest body, long actor) {
        Vehicle value = Vehicle.builder()
            .carrierId(parent.getId())
            .scopeId(parent.getScopeId())
            .code(body.getCode())
            .plateNumber(body.getPlateNumber().strip().toUpperCase(Locale.ROOT))
            .vehicleTypeId(body.getVehicleTypeId())
            .vehicleType(CategoryRefType.VEHICLE_TYPE)
            .capacity(body.getCapacity())
            .active(body.isActive())
            .build();
        return created(value, actor).getId();
    }

    public void updateVehicle(Vehicle current, VehicleRequest body, long actor) {
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

    public long insertDriver(Carrier parent, DriverRequest body, long actor) {
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

    public void updateDriver(Driver current, DriverRequest body, long actor) {
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

    private static void updated(Query query) {
        if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    private static <T extends BaseEntity> T audit(T item, Tuple row) {
        item.setId(longValue(row.get("id")));
        item.setCreatedAt(instantValue(row.get("created_at")));
        item.setUpdatedAt(instantValue(row.get("updated_at")));
        item.setCreatedBy(longValue(row.get("created_by")));
        item.setUpdatedBy(longValue(row.get("updated_by")));
        item.setVersion(longValue(row.get("version")));
        return item;
    }

    private static Carrier carrier(Tuple row) {
        return audit(
            Carrier.builder()
                .scopeId(longValue(row.get("scope_id")))
                .canUpdate(booleanValue(row.get("can_update")))
                .code(stringValue(row.get("code")))
                .name(stringValue(row.get("name")))
                .phone(stringValue(row.get("phone")))
                .description(stringValue(row.get("description")))
                .active(booleanValue(row.get("active")))
                .build(),
            row
        );
    }

    private static Vehicle vehicle(Tuple row) {
        return audit(
            Vehicle.builder()
                .scopeId(longValue(row.get("scope_id")))
                .canUpdate(booleanValue(row.get("can_update")))
                .carrierId(longValue(row.get("carrier_id")))
                .carrierName(stringValue(row.get("carrier_name")))
                .code(stringValue(row.get("code")))
                .plateNumber(stringValue(row.get("plate_number")))
                .vehicleTypeId(longValue(row.get("vehicle_type_id")))
                .vehicleTypeName(stringValue(row.get("vehicle_type_name")))
                .capacity(intValue(row.get("capacity")))
                .active(booleanValue(row.get("active")))
                .build(),
            row
        );
    }

    private static Driver driver(Tuple row) {
        return audit(
            Driver.builder()
                .scopeId(longValue(row.get("scope_id")))
                .canUpdate(booleanValue(row.get("can_update")))
                .canAssign(booleanValue(row.get("can_assign")))
                .carrierId(longValue(row.get("carrier_id")))
                .carrierName(stringValue(row.get("carrier_name")))
                .code(stringValue(row.get("code")))
                .name(stringValue(row.get("name")))
                .phone(stringValue(row.get("phone")))
                .userId(row.get("user_id", Long.class))
                .active(booleanValue(row.get("active")))
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
    FROM {h-schema}carriers c
    JOIN (
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM {h-schema}carriers
    ) s ON s.id = c.scope_id
    WHERE c.active AND s.active
    """;
    private static final String COLUMNS = "c.id,c.code,c.name,c.phone,c.description";

    /** Từ khóa nguyên văn và phân trang ổn định name/id được xử lý hoàn toàn trong PostgreSQL. */
    public PageResponse<Published> page(PublicCarrierSearch body) {
        String where = FROM + " AND LOWER(c.code || ' ' || c.name || ' ' || c.phone) LIKE :keyword ESCAPE '!'";
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        // LIKE tương thích index trigram; escape để %, _ và ! vẫn là ký tự nguyên văn như strpos trước đây.
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + where, Tuple.class);

            query.setParameter("keyword", pattern);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<Published> result = new ArrayList<Published>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                ORDER BY c.name, c.id
                LIMIT :pageSize OFFSET :offset
                """.formatted(COLUMNS, where),
                Tuple.class
            );

            query.setParameter("keyword", pattern);
            query.setParameter("pageSize", body.getSize());
            query.setParameter("offset", body.offset());
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    result.add(map(row));
                }
            }
        }
        return PageResponse.of(result, body, total);
    }

    /** Nhà xe/scope ngừng công bố trả 404; không dùng membership để lọc mất lựa chọn công khai. */
    public Published find(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                    AND c.id = :id
                """.formatted(COLUMNS, FROM),
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                return map(row);
            }
        }
    }

    private Published map(Tuple row) {
        return new Published(
            longValue(row.get(0)),
            stringValue(row.get(1)),
            stringValue(row.get(2)),
            stringValue(row.get(3)),
            stringValue(row.get(4))
        );
    }
}
