package vn.vietroute.auth.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.UserRole;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Repository bảng UserRole; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class UserRoleRepo extends JpaRepositorySupport<UserRole, Long> {

    private final UserRoleCustomRepo batch;

    public UserRoleRepo(EntityManager manager, AppSettings settings, UserRoleCustomRepo batch) {
        super(UserRole.class, manager);
        this.batch = batch;
        scopeCapacity = settings.getScopeCapacity();
    }

    public static final String VISIBLE = """
    EXISTS (
        SELECT 1
        FROM {h-schema}auth_users su
        JOIN {h-schema}carriers security_owner ON security_owner.scope_id = owner.scope_id
            AND security_owner.scope_active
        WHERE su.id = :viewerId AND su.token_version = :viewerVersion AND su.active AND NOT su.deleted
            AND NOT su.activation_pending
            AND NOT su.must_change_password
            AND su.email_verified_at IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM {h-schema}auth_user_roles global_role
                JOIN {h-schema}auth_roles r ON r.code = global_role.role_code AND r.active AND NOT r.deleted
                JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
                JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
                JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
                WHERE global_role.user_id = su.id AND global_role.scope_id = 0
                    AND global_role.active
                    AND fr.target = :viewTarget
                    AND (r.all_scopes
                    OR EXISTS (
                        SELECT 1
                        FROM {h-schema}auth_user_roles scoped_role
                        WHERE scoped_role.user_id = su.id AND scoped_role.role_code = r.code
                            AND scoped_role.scope_id = security_owner.scope_id
                            AND scoped_role.active
                    ))
            )
    )
    """;
    public static final String REQUIRE = """
    SELECT %s
    FROM {h-schema}carriers owner
    WHERE owner.scope_id = :scopeId
    """.formatted(VISIBLE);
    public static final String GLOBAL = """
    SELECT 1
    FROM {h-schema}auth_users u
    JOIN {h-schema}auth_user_roles ur ON ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
    JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
    JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
    JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
    JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
    WHERE u.id = :viewerId AND u.token_version = :viewerVersion AND u.active AND NOT u.deleted
        AND NOT u.activation_pending
        AND NOT u.must_change_password
        AND u.email_verified_at IS NOT NULL
        AND fr.target = :viewTarget
    """;

    private final int scopeCapacity;

    @Transactional(readOnly = true)
    public List<String> roles() {
        List<String> roles = new ArrayList<>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT code
                FROM {h-schema}auth_roles
                WHERE active AND NOT deleted
                ORDER BY code
                LIMIT
                %s
                """.formatted(2048 + 1),
                Tuple.class
            );

            for (Object rowsValue : query.getResultList()) {
                Tuple rows = (Tuple) rowsValue;

                roles.add(stringValue(rows.get(0)));
            }
        }
        if (roles.size() > 2048) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 2048);
        return List.copyOf(roles);
    }

    /** Tất cả mã role cần tồn tại/active, kiểm một statement cho cả tập. */
    public void validateMappings(Set<String> roles, Set<String> unused) {
        String[] codes = roles.stream().toArray(String[]::new);
        {
            Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM {h-schema}auth_roles WHERE code=ANY(:codes) AND active AND NOT deleted",
                Tuple.class
            );

            query.setParameter("codes", codes);
            {
                Tuple row = (Tuple) query.getSingleResult();
                if (longValue(row.get(0)) != roles.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /** Khóa transaction chung cho sửa role user, giữ invariant luôn có ít nhất một admin hoạt động. */
    public void lockRoleAssignments() {
        {
            Query lock = entityManager.createNativeQuery("SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT)");

            lock.getSingleResult();
        }
    }

    /** Snapshot mapping thô cho audit, gồm role đang tắt để việc gỡ không bị mất dấu trong lịch sử. */
    public Set<String> assignedRoles(long id) {
        Set<String> result = new LinkedHashSet<>();
        {
            Query query = entityManager.createNativeQuery(
                "SELECT role_code FROM {h-schema}auth_user_roles WHERE user_id=:id AND scope_id=0 ORDER BY role_code",
                Tuple.class
            );

            query.setParameter("id", id);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(stringValue(rows.get(0)));
                }
            }
        }
        return Set.copyOf(result);
    }

    public void replaceRoles(Connection connection, long id, Set<String> roles, long version, long actor)
        throws SQLException {
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users
                SET version = version + 1, token_version = token_version + 1, updated_at = CURRENT_TIMESTAMP,
                    updated_by = :actor
                WHERE id = :id AND version = :version AND NOT deleted
                """
            );

            query.setParameter("actor", actor);
            query.setParameter("id", id);
            query.setParameter("version", version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        // Lưu mốc thu hồi trước khi FK cascade xóa scoped role cuối; gán lại không nhận version cũ.
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users u
                SET scope_membership_versions = u.scope_membership_versions || v.versions
                FROM (
                    SELECT JSONB_OBJECT_AGG(scope_id::TEXT, next_version) AS versions
                    FROM (
                        SELECT scope_id, GREATEST(MAX(version), COALESCE((
                            SELECT (scope_membership_versions ->> ur.scope_id::TEXT)::BIGINT
                            FROM {h-schema}auth_users
                            WHERE id = :id
                        ), 0)) + 1 AS next_version
                        FROM {h-schema}auth_user_roles ur
                        WHERE user_id = :id AND scope_id > 0
                        GROUP BY scope_id
                        HAVING BOOL_OR(NOT (role_code = ANY(:codes)))
                    ) affected
                ) v
                WHERE u.id = :id AND v.versions IS NOT NULL
                """
            );

            query.setParameter("id", id);
            query.setParameter("codes", roles.toArray(String[]::new));
            query.setParameter("id", id);
            query.executeUpdate();
        }
        batch.replaceRoles(connection, id, roles, actor);
    }

    public boolean protectedUser(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT 1
                FROM {h-schema}auth_user_roles ur
                JOIN {h-schema}auth_roles r ON r.code = ur.role_code
                WHERE ur.user_id = :id AND ur.scope_id = 0 AND r.protected_role
                LIMIT 1
                """,
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowsValues = query.getResultList();
                Tuple rows = rowsValues.isEmpty() ? null : (Tuple) rowsValues.get(0);

                return rows != null;
            }
        }
    }

    /** Đọc cả role đang tắt để không giấu quyền mạnh rồi kích hoạt sau khi gán. */
    public void requireDelegableRoles(Set<String> roles) {
        String[] codes = roles.toArray(String[]::new);
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT r.protected_role, r.all_scopes, fr.target
                FROM {h-schema}auth_roles r
                LEFT JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
                LEFT JOIN {h-schema}auth_function_resources fr ON fr.function_code = rf.function_code
                WHERE r.code = ANY(:codes)
                """,
                Tuple.class
            );

            query.setParameter("codes", codes);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    if (booleanValue(rows.get(0))) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    String target = stringValue(rows.get(2));
                    if (target != null) {
                        UserPrincipal.requireDelegable(Set.of(target));
                        if (
                            booleanValue(rows.get(1)) && !UserPrincipal.current().getGlobalTargets().contains(target)
                        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    }
                }
            }
        }
    }

    public boolean usesRole(String code) {
        {
            Query query = entityManager.createNativeQuery(
                "SELECT 1 FROM {h-schema}auth_user_roles WHERE role_code=:code LIMIT 1",
                Tuple.class
            );

            query.setParameter("code", code);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                return row != null;
            }
        }
    }

    private static final String COLUMNS = """
    scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
        scope_active AS active, scope_version AS version, updated_at, updated_by
    """;

    private static final String MEMBER_KEYS = """
    WITH membership_keys AS (
        SELECT user_id, scope_id
        FROM {h-schema}auth_user_roles
        WHERE scope_id > 0
        UNION
        SELECT u.id, k.key::BIGINT
        FROM {h-schema}auth_users u
        CROSS JOIN LATERAL jsonb_object_keys(u.scope_membership_versions) k(KEY)
    )
    """;

    private static final String MEMBERS = """
    %s
    SELECT m.scope_id, m.user_id, u.username, COALESCE(BOOL_AND(ur.active), FALSE) AS active,
        COALESCE((u.scope_membership_versions ->> m.scope_id::TEXT)::BIGINT, MAX(ur.version), 0) AS version,
        COALESCE(MAX(ur.updated_at), u.updated_at) AS updated_at, COALESCE(MAX(ur.updated_by), u.updated_by)
        AS updated_by, ARRAY_REMOVE(ARRAY_AGG(ur.role_code
    ORDER BY ur.role_code), NULL) AS role_codes
    FROM membership_keys m
    JOIN {h-schema}auth_users u ON u.id = m.user_id
    LEFT JOIN {h-schema}auth_user_roles ur ON ur.user_id = m.user_id AND ur.scope_id = m.scope_id
    """.formatted(MEMBER_KEYS);

    /** Tìm/đếm/phân trang nhà xe ngay tại DB, không phân trang lịch sử trong RAM. */
    @Transactional(readOnly = true)
    public PageResponse<RoleDTO> page(RoleSearch search) {
        String where = """
        FROM {h-schema}carriers
        WHERE (CAST(:active AS BOOLEAN) IS NULL OR scope_active = :active)
            AND (scope_code ILIKE :keyword ESCAPE '!' OR scope_name_vi ILIKE :keyword ESCAPE '!' OR scope_name_en ILIKE :keyword
                ESCAPE '!')
        """;
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + where, Tuple.class);

            bind(query, search);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                %s
                ORDER BY scope_code
                LIMIT :size OFFSET :offset
                """.formatted(COLUMNS, where),
                Tuple.class
            );

            bind(query, search);
            query.setParameter("size", search.getSize());
            query.setParameter("offset", search.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(scope(rows));
                }
            }
        }
        return PageResponse.of(result, search, total);
    }

    public RoleDTO find(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                FROM {h-schema}carriers
                WHERE scope_id = :id
                """.formatted(COLUMNS),
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                return row != null ? scope(row) : null;
            }
        }
    }

    /** Khóa quản trị do caller giữ; hạn mức áp dụng cả nhà xe tắt và ID không bị tái sử dụng. */
    public long allocate() {
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM {h-schema}carriers", Tuple.class);
            Tuple row = (Tuple) query.getSingleResult();
            if (longValue(row.get(0)) >= scopeCapacity) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                scopeCapacity
            );
        }
        {
            Query query = entityManager.createNativeQuery("SELECT nextval('carrier_scope_ids')", Tuple.class);
            Tuple row = (Tuple) query.getSingleResult();
            return longValue(row.get(0));
        }
    }

    /** Chỉ sửa phạm vi của nhà xe có thật; tạo nhà xe qua CarrierService. */
    public long save(long id, RoleRequest body, long actor) {
        if (id <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}carriers
                SET scope_name_vi = :nameVi, scope_name_en = :nameEn, scope_active = :active, scope_version = scope_version + 1,
                    updated_at = CURRENT_TIMESTAMP, updated_by = :actor
                WHERE scope_id = :id AND scope_version = :version
                RETURNING scope_id
                """,
                Tuple.class
            );

            query.setParameter("nameVi", body.getNameVi().strip());
            query.setParameter("nameEn", body.getNameEn().strip());
            query.setParameter("active", Boolean.TRUE.equals(body.getActive()));
            query.setParameter("actor", actor);
            query.setParameter("id", id);
            query.setParameter("version", body.getVersion());
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.STALE_VERSION);
                Tuple row = (Tuple) rowValues.get(0);

                return longValue(row.get(0));
            }
        }
    }

    public RoleDTO member(long scope, long user) {
        {
            Query query = entityManager.createNativeQuery(
                """
                %s
                WHERE m.scope_id = :scope AND m.user_id = :user
                GROUP BY m.scope_id, m.user_id, u.id
                """.formatted(MEMBERS),
                Tuple.class
            );

            query.setParameter("scope", scope);
            query.setParameter("user", user);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                return row != null ? member(row) : null;
            }
        }
    }

    public List<RoleDTO> memberships(long user) {
        ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
        {
            Query query = entityManager.createNativeQuery(
                """
                %s
                WHERE m.user_id = :user
                GROUP BY m.scope_id, m.user_id, u.id
                ORDER BY m.scope_id
                LIMIT 33
                """.formatted(MEMBERS),
                Tuple.class
            );

            query.setParameter("user", user);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(member(rows));
                }
            }
        }
        if (result.size() > 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleDTO> members(long scope, UserSearch search) {
        long total;
        {
            Query query = entityManager.createNativeQuery(
                """
                %s
                SELECT COUNT(*)
                FROM membership_keys
                WHERE scope_id = :scopeId
                    AND (CAST(:userId AS BIGINT) IS NULL OR user_id = :userId)
                """.formatted(MEMBER_KEYS),
                Tuple.class
            );

            bindMember(query, scope, search);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
        {
            Query query = entityManager.createNativeQuery(
                """
                %s
                WHERE m.scope_id = :scopeId
                    AND (CAST(:userId AS BIGINT) IS NULL OR m.user_id = :userId)
                GROUP BY m.scope_id, m.user_id, u.id
                ORDER BY m.user_id
                LIMIT :pageSize OFFSET :offset
                """.formatted(MEMBERS),
                Tuple.class
            );

            bindMember(query, scope, search);
            query.setParameter("pageSize", search.getSize());
            query.setParameter("offset", search.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(member(rows));
                }
            }
        }
        return PageResponse.of(result, search, total);
    }

    /** CAS dưới khóa quản trị; thay toàn bộ tập bằng INSERT SELECT, giữ FK scoped là tập con global. */
    public void assign(Connection connection, long scope, long user, UserRequest body, long actor) throws SQLException {
        RoleDTO before = member(scope, user);
        long version = before == null ? 0 : before.getVersion();
        if (body.getVersion() != version) throw new BusinessException(ErrorCode.STALE_VERSION);
        if (before == null) {
            Query query = entityManager.createNativeQuery(
                """
                %s
                SELECT COUNT(*)
                FROM membership_keys
                WHERE user_id = :user
                """.formatted(MEMBER_KEYS),
                Tuple.class
            );

            query.setParameter("user", user);
            {
                Tuple row = (Tuple) query.getSingleResult();
                if (longValue(row.get(0)) >= 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
            }
        }
        // Giữ mốc version kể cả tập role rỗng; advisory lock chung bảo vệ CAS với gán role toàn cục.
        {
            Query query = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users
                SET scope_membership_versions = JSONB_SET(scope_membership_versions, ARRAY [CAST(:scope AS TEXT)],
                    TO_JSONB(CAST(:nextVersion AS BIGINT)),
                    TRUE), version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = :actor
                WHERE id = :user
                """
            );

            query.setParameter("scope", scope);
            query.setParameter("nextVersion", version + 1);
            query.setParameter("actor", actor);
            query.setParameter("user", user);
            query.executeUpdate();
        }
        batch.assign(connection, scope, user, body, actor, version);
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> mine(long user) {
        ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                FROM {h-schema}carriers c
                WHERE c.scope_active
                    AND EXISTS (
                        SELECT 1
                        FROM {h-schema}auth_user_roles ur
                        JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
                        JOIN {h-schema}auth_users u ON u.id = ur.user_id AND u.active AND NOT u.deleted
                            AND NOT u.activation_pending
                            AND NOT u.must_change_password
                            AND u.email_verified_at IS NOT NULL
                        WHERE ur.user_id = :user AND ur.scope_id = c.scope_id AND ur.active
                    )
                ORDER BY c.scope_code
                LIMIT 32
                """.formatted(COLUMNS),
                Tuple.class
            );

            query.setParameter("user", user);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(scope(rows));
                }
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public Set<String> targets(long user, long tokenVersion, long scope) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT ARRAY(
                    SELECT DISTINCT fr.target
                    FROM {h-schema}auth_user_roles ur
                    JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
                    JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
                    JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
                    JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
                    WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
                        AND (r.all_scopes
                        OR EXISTS (
                            SELECT 1
                            FROM {h-schema}auth_user_roles sr
                            WHERE sr.user_id = u.id AND sr.role_code = r.code AND sr.scope_id = c.scope_id
                                AND sr.active
                        ))
                ) AS resource_targets
                FROM {h-schema}auth_users u
                JOIN {h-schema}carriers c ON c.scope_id = :scope AND c.scope_active
                WHERE u.id = :user AND u.token_version = :tokenVersion AND u.active AND NOT u.deleted
                    AND NOT u.activation_pending
                    AND NOT u.must_change_password
                    AND u.email_verified_at IS NOT NULL
                """,
                Tuple.class
            );

            query.setParameter("scope", scope);
            query.setParameter("user", user);
            query.setParameter("tokenVersion", tokenVersion);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                Tuple row = (Tuple) rowValues.get(0);

                return UserRepo.targets(row);
            }
        }
    }

    private static void bind(Query query, RoleSearch search) {
        query.setParameter("active", search.getActive());
        String term = "%" + search.getQuery().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        query.setParameter("keyword", term);
    }

    private static void bindMember(Query query, long scope, UserSearch search) {
        query.setParameter("scopeId", scope);
        query.setParameter("userId", search.getUserId());
    }

    private static RoleDTO scope(Tuple row) {
        RoleDTO dto = new RoleDTO();
        dto.setId(longValue(row.get("id")));
        dto.setScopeId(longValue(row.get("id")));
        dto.setCode(stringValue(row.get("code")));
        dto.setNameVi(stringValue(row.get("name_vi")));
        dto.setNameEn(stringValue(row.get("name_en")));
        dto.setActive(booleanValue(row.get("active")));
        dto.setVersion(longValue(row.get("version")));
        dto.setUpdatedAt(instantValue(row.get("updated_at")));
        dto.setUpdatedBy(longValue(row.get("updated_by")));
        return dto;
    }

    private static RoleDTO member(Tuple row) {
        RoleDTO dto = new RoleDTO();
        dto.setScopeId(longValue(row.get("scope_id")));
        dto.setUserId(longValue(row.get("user_id")));
        dto.setUsername(stringValue(row.get("username")));
        dto.setRoles(UserRepo.strings(row, "role_codes"));
        dto.setActive(booleanValue(row.get("active")));
        dto.setVersion(longValue(row.get("version")));
        dto.setUpdatedAt(instantValue(row.get("updated_at")));
        dto.setUpdatedBy(longValue(row.get("updated_by")));
        return dto;
    }

    public static String visibleSql() {
        return VISIBLE;
    }

    /** Chỉ đổi tên bind của endpoint đích, giữ nguyên toàn bộ predicate scope/role/function. */
    public static String visibleSql(String targetParameter) {
        if (!Set.of("viewTarget", "updateTarget", "assignTarget").contains(targetParameter)) {
            throw new IllegalArgumentException("Tên tham số scope không hợp lệ");
        }
        return VISIBLE.replace(":viewTarget", ":" + targetParameter);
    }

    public static void bindVisible(Query query) {
        bindVisible(query, UserPrincipal.current().getTarget(), "viewTarget");
    }

    /** Khả năng UPDATE kiểm endpoint đích riêng, không dùng quyền GET của trang đang xem. */
    public static void bindVisible(Query query, String target) {
        bindVisible(query, target, "updateTarget");
    }

    public static void bindVisible(Query query, String target, String targetParameter) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (
            auth == null ||
            !(auth.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id() == null ||
            actor.id() <= 0 ||
            target == null
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        visibleSql(targetParameter);
        query.setParameter("viewerId", actor.id());
        query.setParameter("viewerVersion", actor.tokenVersion());
        query.setParameter(targetParameter, target);
    }

    /** Kiểm trạng thái hiện hành trên connection caller, không dùng quyền/phạm vi JWT làm nguồn sự thật. */
    public void require(long scopeId) {
        {
            Query query = entityManager.createNativeQuery(REQUIRE, Tuple.class);

            bindVisible(query);
            query.setParameter("scopeId", scopeId);
            {
                List<?> rowValues = query.getResultList();
                Tuple row = rowValues.isEmpty() ? null : (Tuple) rowValues.get(0);

                if (!(row != null) || !booleanValue(row.get(0))) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    public void requireGlobal(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        {
            Query query = entityManager.createNativeQuery(GLOBAL, Tuple.class);

            bindVisible(query);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                Tuple row = (Tuple) rowValues.get(0);
            }
        }
    }

    public static void requireSameScope(long parentScope, long childScope) {
        if (parentScope < 1 || parentScope != childScope) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    /** ID scope kỹ thuật được cấp trong transaction tạo Carrier; chưa tồn tại Carrier thì không cấp membership. */
    public long create(String name, long actor) {
        UserPrincipal current = UserPrincipal.current();
        BusinessRules.require(current.id() == actor, ErrorCode.ACCESS_DENIED);
        requireGlobal(current);
        lockRoleAssignments();
        return allocate();
    }
}
