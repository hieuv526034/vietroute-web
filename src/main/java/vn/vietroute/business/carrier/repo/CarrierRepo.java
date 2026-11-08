package vn.vietroute.business.carrier.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.projection.CarrierLookupProjection;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.request.DriverRequest;
import vn.vietroute.business.carrier.dto.request.VehicleRequest;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của CarrierRepo; transaction ghi thuộc Service.
 */
public interface CarrierRepo extends JpaRepository<Carrier, Long> {
    String CARRIER_COLUMNS =
        """
        owner.id, owner.scope_id, owner.code, owner.name, owner.phone, owner.description, owner.active,
        owner.created_at, owner.updated_at, owner.created_by, owner.updated_by, owner.version, (
        """ +
        UserRoleRepo.UPDATE_VISIBLE +
        ") AS can_update ";

    String CARRIER_FILTER =
        "FROM {h-schema}carriers owner WHERE " +
        UserRoleRepo.VISIBLE +
        """
        AND (CAST(:active AS BOOLEAN) IS NULL OR owner.active = :active)
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND LOWER(owner.code || ' ' || owner.name || ' ' || owner.phone) LIKE :keyword ESCAPE '\\'
        """;

    @Query(value = "SELECT COUNT(*) " + CARRIER_FILTER, nativeQuery = true)
    long carrierCount(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword
    );

    @Query(
        value = "SELECT " + CARRIER_COLUMNS + CARRIER_FILTER + "ORDER BY owner.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> carrierRows(
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
    default PageResponse<Carrier> carriers(CarrierSearch body) {
        return carrierPage(body);
    }

    private PageResponse<Carrier> carrierPage(CarrierSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = pattern(text);
        long total = carrierCount(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword
        );
        List<Carrier> rows = carrierRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword,
            "PUT " + Routes.ADMIN_CARRIERS + Routes.BY_ID,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(CarrierRepo::mapCarrier)
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = "SELECT " +
            CARRIER_COLUMNS +
            "FROM {h-schema}carriers owner WHERE owner.id = :id AND " +
            UserRoleRepo.VISIBLE,
        nativeQuery = true
    )
    Map<String, Object> carrierRow(long viewerId, long viewerVersion, String viewTarget, String updateTarget, long id);

    @Query(
        value = "SELECT " +
            CARRIER_COLUMNS +
            "FROM {h-schema}carriers owner WHERE owner.id = :id AND " +
            UserRoleRepo.VISIBLE +
            "FOR UPDATE OF owner ",
        nativeQuery = true
    )
    Map<String, Object> carrierLockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long id
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default Carrier carrier(long id, boolean lock) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_CARRIERS + Routes.BY_ID;
        Map<String, Object> row = lock
            ? carrierLockedRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id)
            : carrierRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id);
        return mapCarrier(BusinessRules.requireFound(row == null || row.isEmpty() ? null : row));
    }

    String VEHICLE_COLUMNS =
        """
        item.id, item.scope_id, item.carrier_id, owner.name AS carrier_name, item.code, item.plate_number,
        item.vehicle_type_id, vt.name AS vehicle_type_name, item.capacity, item.active,
        item.created_at, item.updated_at, item.created_by, item.updated_by, item.version, (
        """ +
        UserRoleRepo.UPDATE_VISIBLE +
        ") AS can_update ";

    String VEHICLE_FILTER =
        """
        FROM {h-schema}carrier_vehicles item
        JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
        JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type= 'VEHICLE_TYPE' AND vt.type= 'VALUE'
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
        AND (CAST(:active AS BOOLEAN) IS NULL OR item.active = :active)
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND LOWER(CONCAT_WS(' ', item.code, item.plate_number, owner.name)) LIKE :keyword ESCAPE '\\'
        """;

    @Query(value = "SELECT COUNT(*) " + VEHICLE_FILTER, nativeQuery = true)
    long vehicleCount(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword
    );

    @Query(
        value = "SELECT " + VEHICLE_COLUMNS + VEHICLE_FILTER + "ORDER BY item.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> vehicleRows(
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
    default PageResponse<Vehicle> vehicles(CarrierSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = pattern(text);
        long total = vehicleCount(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword
        );
        List<Vehicle> rows = vehicleRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword,
            "PUT " + Routes.ADMIN_VEHICLES + Routes.BY_ID,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(CarrierRepo::mapVehicle)
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = "SELECT " +
            VEHICLE_COLUMNS +
            """
            FROM {h-schema}carrier_vehicles item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type= 'VEHICLE_TYPE' AND vt.type= 'VALUE'
            WHERE item.id = :id AND
            """ +
            UserRoleRepo.VISIBLE,
        nativeQuery = true
    )
    Map<String, Object> vehicleRow(long viewerId, long viewerVersion, String viewTarget, String updateTarget, long id);

    @Query(
        value = "SELECT " +
            VEHICLE_COLUMNS +
            """
            FROM {h-schema}carrier_vehicles item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            JOIN {h-schema}category vt ON vt.id = item.vehicle_type_id AND vt.ref_type= 'VEHICLE_TYPE' AND vt.type= 'VALUE'
            WHERE item.id = :id AND
            """ +
            UserRoleRepo.VISIBLE +
            "FOR UPDATE OF item ",
        nativeQuery = true
    )
    Map<String, Object> vehicleLockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        long id
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default Vehicle vehicle(long id, boolean lock) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_VEHICLES + Routes.BY_ID;
        Map<String, Object> row = lock
            ? vehicleLockedRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id)
            : vehicleRow(actor.id(), actor.tokenVersion(), actor.getTarget(), update, id);
        return mapVehicle(BusinessRules.requireFound(row == null || row.isEmpty() ? null : row));
    }

    String DRIVER_COLUMNS =
        """
        item.id, item.scope_id, item.carrier_id, owner.name AS carrier_name, item.code, item.name, item.phone,
        item.user_id, item.active, item.created_at, item.updated_at, item.created_by, item.updated_by, item.version, (
        """ +
        UserRoleRepo.UPDATE_VISIBLE +
        ") AS can_update, ( " +
        UserRoleRepo.ASSIGN_VISIBLE +
        ") AS can_assign ";

    String DRIVER_FILTER =
        """
        FROM {h-schema}carrier_drivers item
        JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
        WHERE
        """ +
        UserRoleRepo.VISIBLE +
        """
        AND (CAST(:active AS BOOLEAN) IS NULL OR item.active = :active)
        AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
        AND LOWER(CONCAT_WS(' ', item.code, item.name, item.phone, owner.name)) LIKE :keyword ESCAPE '\\'
        """;

    @Query(value = "SELECT COUNT(*) " + DRIVER_FILTER, nativeQuery = true)
    long driverCount(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword
    );

    @Query(
        value = "SELECT " + DRIVER_COLUMNS + DRIVER_FILTER + "ORDER BY item.id DESC LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<Map<String, Object>> driverRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        Boolean active,
        Long carrierId,
        String keyword,
        String updateTarget,
        String assignTarget,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<Driver> drivers(CarrierSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = pattern(text);
        long total = driverCount(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword
        );
        List<Driver> rows = driverRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getActive(),
            body.getCarrierId(),
            keyword,
            "PUT " + Routes.ADMIN_DRIVERS + Routes.BY_ID,
            "POST " + Routes.ADMIN_DRIVERS + Routes.DRIVER_ACCOUNT,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(CarrierRepo::mapDriver)
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = "SELECT " +
            DRIVER_COLUMNS +
            """
            FROM {h-schema}carrier_drivers item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            WHERE item.id = :id AND
            """ +
            UserRoleRepo.VISIBLE,
        nativeQuery = true
    )
    Map<String, Object> driverRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        String assignTarget,
        long id
    );

    @Query(
        value = "SELECT " +
            DRIVER_COLUMNS +
            """
            FROM {h-schema}carrier_drivers item
            JOIN {h-schema}carriers owner ON owner.id = item.carrier_id AND owner.scope_id = item.scope_id
            WHERE item.id = :id AND
            """ +
            UserRoleRepo.VISIBLE +
            "FOR UPDATE OF item ",
        nativeQuery = true
    )
    Map<String, Object> driverLockedRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String updateTarget,
        String assignTarget,
        long id
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default Driver driver(long id, boolean lock) {
        UserPrincipal actor = UserPrincipal.current();
        String update = "PUT " + Routes.ADMIN_DRIVERS + Routes.BY_ID;
        Map<String, Object> row = lock
            ? driverLockedRow(
                  actor.id(),
                  actor.tokenVersion(),
                  actor.getTarget(),
                  update,
                  "POST " + Routes.ADMIN_DRIVERS + Routes.DRIVER_ACCOUNT,
                  id
              )
            : driverRow(
                  actor.id(),
                  actor.tokenVersion(),
                  actor.getTarget(),
                  update,
                  "POST " + Routes.ADMIN_DRIVERS + Routes.DRIVER_ACCOUNT,
                  id
              );
        return mapDriver(BusinessRules.requireFound(row == null || row.isEmpty() ? null : row));
    }

    @Transactional(readOnly = true)
    default PageResponse<Carrier> choices(CarrierSearch body) {
        return carrierPage(body);
    }

    @Query(
        value = """
        INSERT INTO {h-schema}carriers(scope_id, code, name, phone, description, active,
            scope_code, scope_name_vi, scope_name_en, scope_active, scope_version, created_by, updated_by)
        VALUES (:scope, :code, :name, :phone, :description, :active, :scopeCode, :name, :name, TRUE, 0, :actor, :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertCarrierRow(
        long scope,
        String code,
        String name,
        String phone,
        String description,
        boolean active,
        String scopeCode,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insertCarrier(long scope, CarrierRequest body, long actor) {
        return insertCarrierRow(
            scope,
            body.getCode(),
            body.getName().strip(),
            body.getPhone().strip(),
            body.getDescription().strip(),
            body.isActive(),
            "CARRIER_" + scope,
            actor
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carriers
        SET name = :name, phone = :phone, description = :description, active = :active,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor, version = version + 1
        WHERE id = :id AND scope_id = :scope AND version = :version
        """,
        nativeQuery = true
    )
    int updateCarrierRow(
        long id,
        long scope,
        Long version,
        String name,
        String phone,
        String description,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void updateCarrier(Carrier current, CarrierRequest body, long actor) {
        BusinessRules.require(
            body.getCode() == null || current.getCode().equals(body.getCode()),
            ErrorCode.INVALID_REQUEST
        );
        BusinessRules.require(
            updateCarrierRow(
                current.getId(),
                current.getScopeId(),
                body.getVersion(),
                body.getName().strip(),
                body.getPhone().strip(),
                body.getDescription().strip(),
                body.isActive(),
                actor
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Query(
        value = """
        SELECT id
        FROM {h-schema}category
        WHERE id = :id AND ref_type = 'VEHICLE_TYPE' AND type = 'VALUE' AND active
        FOR SHARE
        """,
        nativeQuery = true
    )
    List<Long> vehicleTypeRows(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default void requireVehicleType(long id) {
        BusinessRules.require(!vehicleTypeRows(id).isEmpty(), ErrorCode.INVALID_REQUEST);
    }

    @Query(
        value = """
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
        FOR SHARE OF u
        """,
        nativeQuery = true
    )
    List<Long> driverUserRows(long id, long scope);

    @Transactional(propagation = Propagation.MANDATORY)
    default void requireDriverUser(long id, long scope) {
        BusinessRules.require(!driverUserRows(id, scope).isEmpty(), ErrorCode.INVALID_REQUEST);
    }

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1 FROM {h-schema}carrier_trips
            WHERE driver_id = :id AND status IN ('DRAFT', 'OPEN', 'IN_PROGRESS')
        )
        """,
        nativeQuery = true
    )
    boolean driverAssigned(long id);

    default void requireDriverUnassigned(long id) {
        BusinessRules.require(!driverAssigned(id), ErrorCode.RESOURCE_IN_USE);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_drivers
        SET user_id = :user, active = FALSE, updated_by = :actor, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :currentId AND carrier_id = :carrierId AND scope_id = :scopeId AND user_id IS NULL AND version =
            :currentVersion
        """,
        nativeQuery = true
    )
    int linkDriverRow(long user, long actor, long currentId, long carrierId, long scopeId, long currentVersion);

    @Transactional(propagation = Propagation.MANDATORY)
    default void linkProvisionedDriver(Driver current, long user, long actor) {
        BusinessRules.require(
            linkDriverRow(
                user,
                actor,
                current.getId(),
                current.getCarrierId(),
                current.getScopeId(),
                current.getVersion()
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Query(
        value = """
        INSERT INTO {h-schema}carrier_vehicles(carrier_id, scope_id, code, plate_number, vehicle_type_id,
            vehicle_type, capacity, active, created_by, updated_by)
        VALUES (:carrierId, :scope, :code, :plateNumber, :vehicleTypeId, 'VEHICLE_TYPE', :capacity, :active, :actor,
            :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertVehicleRow(
        long carrierId,
        long scope,
        String code,
        String plateNumber,
        long vehicleTypeId,
        int capacity,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insertVehicle(Carrier parent, VehicleRequest body, long actor) {
        return insertVehicleRow(
            parent.getId(),
            parent.getScopeId(),
            body.getCode(),
            body.getPlateNumber().strip().toUpperCase(Locale.ROOT),
            body.getVehicleTypeId(),
            body.getCapacity(),
            body.isActive(),
            actor
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_vehicles
        SET plate_number = :plateNumber, vehicle_type_id = :vehicleTypeId, capacity = :capacity, active = :active,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor, version = version + 1
        WHERE id = :id AND carrier_id = :carrierId AND scope_id = :scope AND version = :version
        """,
        nativeQuery = true
    )
    int updateVehicleRow(
        long id,
        long carrierId,
        long scope,
        Long version,
        String plateNumber,
        long vehicleTypeId,
        int capacity,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void updateVehicle(Vehicle current, VehicleRequest body, long actor) {
        BusinessRules.require(
            (body.getCode() == null || current.getCode().equals(body.getCode())) &&
                (body.getCarrierId() == null || current.getCarrierId() == body.getCarrierId()),
            ErrorCode.INVALID_REQUEST
        );
        BusinessRules.require(
            updateVehicleRow(
                current.getId(),
                current.getCarrierId(),
                current.getScopeId(),
                body.getVersion(),
                body.getPlateNumber().strip().toUpperCase(Locale.ROOT),
                body.getVehicleTypeId(),
                body.getCapacity(),
                body.isActive(),
                actor
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    @Query(
        value = """
        INSERT INTO {h-schema}carrier_drivers(carrier_id, scope_id, code, name, phone, user_id, active, created_by,
            updated_by)
        VALUES (:carrierId, :scope, :code, :name, :phone, :userId, :active, :actor, :actor)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertDriverRow(
        long carrierId,
        long scope,
        String code,
        String name,
        String phone,
        Long userId,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long insertDriver(Carrier parent, DriverRequest body, long actor) {
        return insertDriverRow(
            parent.getId(),
            parent.getScopeId(),
            body.getCode(),
            body.getName().strip(),
            body.getPhone().strip(),
            body.getUserId(),
            body.isActive(),
            actor
        );
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}carrier_drivers
        SET name = :name, phone = :phone, user_id = :userId, active = :active,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor, version = version + 1
        WHERE id = :id AND carrier_id = :carrierId AND scope_id = :scope AND version = :version
        """,
        nativeQuery = true
    )
    int updateDriverRow(
        long id,
        long carrierId,
        long scope,
        Long version,
        String name,
        String phone,
        Long userId,
        boolean active,
        long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void updateDriver(Driver current, DriverRequest body, long actor) {
        BusinessRules.require(
            (body.getCode() == null || current.getCode().equals(body.getCode())) &&
                (body.getCarrierId() == null || current.getCarrierId() == body.getCarrierId()),
            ErrorCode.INVALID_REQUEST
        );
        BusinessRules.require(
            updateDriverRow(
                current.getId(),
                current.getCarrierId(),
                current.getScopeId(),
                body.getVersion(),
                body.getName().strip(),
                body.getPhone().strip(),
                body.getUserId(),
                body.isActive(),
                actor
            ) == 1,
            ErrorCode.STALE_VERSION
        );
    }

    private static <T extends BaseEntity> T audit(T item, Map<String, Object> row) {
        item.setId(longValue(row.get("id")));
        item.setCreatedAt(instantValue(row.get("created_at")));
        item.setUpdatedAt(instantValue(row.get("updated_at")));
        item.setCreatedBy(longValue(row.get("created_by")));
        item.setUpdatedBy(longValue(row.get("updated_by")));
        item.setVersion(longValue(row.get("version")));
        return item;
    }

    private static Carrier mapCarrier(Map<String, Object> row) {
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

    private static Vehicle mapVehicle(Map<String, Object> row) {
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

    private static Driver mapDriver(Map<String, Object> row) {
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
                .userId((Long) row.get("user_id"))
                .active(booleanValue(row.get("active")))
                .build(),
            row
        );
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

    String PUBLIC_FILTER = """
    FROM {h-schema}carriers c
    JOIN (
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM {h-schema}carriers
    ) s ON s.id = c.scope_id
    WHERE c.active AND s.active AND LOWER(c.code || ' ' || c.name || ' ' || c.phone) LIKE :keyword ESCAPE '!'
    """;

    @Query(value = "SELECT COUNT(*) " + PUBLIC_FILTER, nativeQuery = true)
    long publishedCount(String keyword);

    @Query(
        value = "SELECT c.id, c.code, c.name, c.phone, c.description " +
            PUBLIC_FILTER +
            "ORDER BY c.name, c.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<CarrierLookupProjection> publishedRows(String keyword, int pageSize, long offset);

    @Transactional(readOnly = true)
    default PageResponse<Published> page(PublicCarrierSearch body) {
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = publishedCount(keyword);
        return PageResponse.of(
            publishedRows(keyword, body.getSize(), body.offset()).stream().map(CarrierRepo::published).toList(),
            body,
            total
        );
    }

    @Query(
        value = """
        SELECT c.id, c.code, c.name, c.phone, c.description
        FROM {h-schema}carriers c
        JOIN (
            SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                scope_active AS active, scope_version AS version, updated_at, updated_by
            FROM {h-schema}carriers
        ) s ON s.id = c.scope_id
        WHERE c.active AND s.active AND c.id = :id
        """,
        nativeQuery = true
    )
    List<CarrierLookupProjection> publishedById(long id);

    @Transactional(readOnly = true)
    default Published find(long id) {
        return published(BusinessRules.requireFound(publishedById(id).stream().findFirst().orElse(null)));
    }

    private static Published published(CarrierLookupProjection row) {
        return new Published(row.getId(), row.getCode(), row.getName(), row.getPhone(), row.getDescription());
    }

    private static String pattern(String search) {
        return "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
