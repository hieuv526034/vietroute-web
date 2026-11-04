package vn.vietroute.auth.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.UserRole;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native của UserRole; transaction ghi do Service/Initializer sở hữu.
 */
public interface UserRoleRepo extends JpaRepository<UserRole, Long> {
    String VISIBLE_PREFIX = """
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
                AND fr.target =
    """;

    String VISIBLE_SUFFIX =
        "\n" +
        """
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

    String VISIBLE = VISIBLE_PREFIX + ":viewTarget" + VISIBLE_SUFFIX;

    String UPDATE_VISIBLE = VISIBLE_PREFIX + ":updateTarget" + VISIBLE_SUFFIX;

    String ASSIGN_VISIBLE = VISIBLE_PREFIX + ":assignTarget" + VISIBLE_SUFFIX;

    @Query(
        value = "SELECT code FROM {h-schema}auth_roles WHERE active AND NOT deleted ORDER BY code LIMIT 2049 ",
        nativeQuery = true
    )
    List<String> roleCodes();

    @Transactional(readOnly = true)
    default List<String> roles() {
        List<String> codes = roleCodes();
        if (codes.size() > 2048) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 2048);
        return codes;
    }

    @Query(
        value = "SELECT COUNT(*) FROM {h-schema}auth_roles WHERE code IN (:codes) AND active AND NOT deleted ",
        nativeQuery = true
    )
    long availableRoles(@Param("codes") Set<String> codes);

    /**
     * Kiểm cả tập mã bằng một query; tập rỗng không sinh IN rỗng.
     */
    default void validateMappings(Set<String> roles, Set<String> unused) {
        if (!roles.isEmpty()) BusinessRules.require(availableRoles(roles) == roles.size(), ErrorCode.INVALID_REQUEST);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT) ", nativeQuery = true)
    String lockRoleAssignments();

    @Query(
        value = "SELECT role_code FROM {h-schema}auth_user_roles WHERE user_id = :id AND scope_id = 0 ORDER BY role_code ",
        nativeQuery = true
    )
    Set<String> assignedRoles(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET version = version + 1, token_version = token_version + 1, updated_at = CURRENT_TIMESTAMP,
            updated_by = :actor
        WHERE id = :id AND version = :version AND NOT deleted
        """,
        nativeQuery = true
    )
    int versionRoles(@Param("id") long id, @Param("version") long version, @Param("actor") long actor);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
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
                HAVING BOOL_OR(NOT (role_code = ANY(CAST(:codes AS TEXT []))))
        ) affected
        ) v
        WHERE u.id = :id AND v.versions IS NOT NULL
        """,
        nativeQuery = true
    )
    int rememberMembershipVersions(@Param("id") long id, @Param("codes") String[] codes);

    /**
     * CAS và mốc thu hồi chạy trước ghi batch mapping trong transaction Service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    default void prepareRoles(long id, Set<String> roles, long version, long actor) {
        BusinessRules.require(versionRoles(id, version, actor) == 1, ErrorCode.STALE_VERSION);
        rememberMembershipVersions(id, roles.toArray(String[]::new));
    }

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code
            WHERE ur.user_id = :id AND ur.scope_id = 0 AND r.protected_role
        )
        """,
        nativeQuery = true
    )
    boolean protectedUser(@Param("id") long id);

    @Query(
        value = """
        SELECT r.protected_role, r.all_scopes, fr.target
        FROM {h-schema}auth_roles r
        LEFT JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
        LEFT JOIN {h-schema}auth_function_resources fr ON fr.function_code = rf.function_code
        WHERE r.code IN (:codes)
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> delegableRows(@Param("codes") Set<String> roles);

    /**
     * Role/function đang tắt vẫn được tính khi kiểm trần cấp quyền.
     */
    default void requireDelegableRoles(Set<String> roles) {
        if (roles.isEmpty()) return;
        for (Map<String, Object> row : delegableRows(roles)) {
            BusinessRules.require(!booleanValue(row.get("protected_role")), ErrorCode.ACCESS_DENIED);
            String target = stringValue(row.get("target"));
            if (target != null) {
                UserPrincipal.requireDelegable(Set.of(target));
                BusinessRules.require(
                    !booleanValue(row.get("all_scopes")) || UserPrincipal.current().getGlobalTargets().contains(target),
                    ErrorCode.ACCESS_DENIED
                );
            }
        }
    }

    @Query(
        value = "SELECT EXISTS ( SELECT 1 FROM {h-schema}auth_user_roles WHERE role_code = :code ) ",
        nativeQuery = true
    )
    boolean usesRole(@Param("code") String code);

    @Query(
        value = """
        SELECT COUNT(*)
        FROM {h-schema}carriers
        WHERE (CAST(:active AS BOOLEAN) IS NULL OR scope_active = :active)
            AND (scope_code ILIKE :keyword ESCAPE '!' OR scope_name_vi ILIKE :keyword ESCAPE '!' OR scope_name_en ILIKE
                :keyword
            ESCAPE '!')
        """,
        nativeQuery = true
    )
    long scopeCount(@Param("active") Boolean active, @Param("keyword") String keyword);

    @Query(
        value = """
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM {h-schema}carriers
        WHERE (CAST(:active AS BOOLEAN) IS NULL OR scope_active = :active)
            AND (scope_code ILIKE :keyword ESCAPE '!' OR scope_name_vi ILIKE :keyword ESCAPE '!' OR scope_name_en ILIKE
                :keyword
            ESCAPE '!')
        ORDER BY scope_code
        LIMIT :size OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> scopeRows(
        @Param("active") Boolean active,
        @Param("keyword") String keyword,
        @Param("size") int size,
        @Param("offset") long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<RoleDTO> page(RoleSearch search) {
        String term = "%" + search.getQuery().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = scopeCount(search.getActive(), term);
        List<RoleDTO> rows = scopeRows(search.getActive(), term, search.getSize(), search.offset())
            .stream()
            .map(UserRoleRepo::scope)
            .toList();
        return PageResponse.of(rows, search, total);
    }

    @Query(
        value = """
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM {h-schema}carriers
        WHERE scope_id = :id
        """,
        nativeQuery = true
    )
    Map<String, Object> scopeRow(@Param("id") long id);

    default RoleDTO find(long id) {
        Map<String, Object> row = scopeRow(id);
        return row == null || row.isEmpty() ? null : scope(row);
    }

    @Query(value = "SELECT COUNT(*) FROM {h-schema}carriers ", nativeQuery = true)
    long scopeCount();

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT NEXTVAL(CAST(:sequence AS REGCLASS)) ", nativeQuery = true)
    long nextScopeId(@Param("sequence") String sequence);

    @Query(
        value = """
        UPDATE {h-schema}carriers
        SET scope_name_vi = :nameVi, scope_name_en = :nameEn, scope_active = :active, scope_version = scope_version + 1,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor
        WHERE scope_id = :id AND scope_version = :version
        RETURNING scope_id
        """,
        nativeQuery = true
    )
    Long saveRow(
        @Param("id") long id,
        @Param("version") Long version,
        @Param("nameVi") String nameVi,
        @Param("nameEn") String nameEn,
        @Param("active") boolean active,
        @Param("actor") long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default long save(long id, RoleRequest body, long actor) {
        BusinessRules.require(id > 0, ErrorCode.INVALID_REQUEST);
        Long saved = saveRow(
            id,
            body.getVersion(),
            body.getNameVi().strip(),
            body.getNameEn().strip(),
            Boolean.TRUE.equals(body.getActive()),
            actor
        );
        BusinessRules.require(saved != null, ErrorCode.STALE_VERSION);
        return saved;
    }

    @Query(
        value = """
        WITH membership_keys AS (
            SELECT user_id, scope_id
            FROM {h-schema}auth_user_roles
            WHERE scope_id > 0
            UNION
            SELECT u.id, k.key::BIGINT
            FROM {h-schema}auth_users u
            CROSS JOIN LATERAL JSONB_OBJECT_KEYS(u.scope_membership_versions) k(KEY)
        )
        SELECT m.scope_id, m.user_id, u.username, COALESCE(BOOL_AND(ur.active), FALSE) AS active,
            COALESCE((u.scope_membership_versions ->> m.scope_id::TEXT)::BIGINT, MAX(ur.version), 0) AS version,
            COALESCE(MAX(ur.updated_at), u.updated_at) AS updated_at, COALESCE(MAX(ur.updated_by), u.updated_by)
            AS updated_by, ARRAY_REMOVE(ARRAY_AGG(ur.role_code
                ORDER BY ur.role_code), NULL) AS role_codes
        FROM membership_keys m
        JOIN {h-schema}auth_users u ON u.id = m.user_id
        LEFT JOIN {h-schema}auth_user_roles ur ON ur.user_id = m.user_id AND ur.scope_id = m.scope_id
        WHERE m.scope_id = :scope AND m.user_id = :user
        GROUP BY m.scope_id, m.user_id, u.id
        """,
        nativeQuery = true
    )
    Map<String, Object> memberRow(@Param("scope") long scope, @Param("user") long user);

    default RoleDTO member(long scope, long user) {
        Map<String, Object> row = memberRow(scope, user);
        return row == null || row.isEmpty() ? null : member(row);
    }

    @Query(
        value = """
        WITH membership_keys AS (
            SELECT user_id, scope_id
            FROM {h-schema}auth_user_roles
            WHERE scope_id > 0
            UNION
            SELECT u.id, k.key::BIGINT
            FROM {h-schema}auth_users u
            CROSS JOIN LATERAL JSONB_OBJECT_KEYS(u.scope_membership_versions) k(KEY)
        )
        SELECT m.scope_id, m.user_id, u.username, COALESCE(BOOL_AND(ur.active), FALSE) AS active,
            COALESCE((u.scope_membership_versions ->> m.scope_id::TEXT)::BIGINT, MAX(ur.version), 0) AS version,
            COALESCE(MAX(ur.updated_at), u.updated_at) AS updated_at, COALESCE(MAX(ur.updated_by), u.updated_by)
            AS updated_by, ARRAY_REMOVE(ARRAY_AGG(ur.role_code
                ORDER BY ur.role_code), NULL) AS role_codes
        FROM membership_keys m
        JOIN {h-schema}auth_users u ON u.id = m.user_id
        LEFT JOIN {h-schema}auth_user_roles ur ON ur.user_id = m.user_id AND ur.scope_id = m.scope_id
        WHERE m.user_id = :user
        GROUP BY m.scope_id, m.user_id, u.id
        ORDER BY m.scope_id
        LIMIT 33
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> membershipRows(@Param("user") long user);

    default List<RoleDTO> memberships(long user) {
        List<Map<String, Object>> rows = membershipRows(user);
        BusinessRules.require(rows.size() <= 32, ErrorCode.ACCESS_CAPACITY_REACHED, 32);
        return rows.stream().map(UserRoleRepo::member).toList();
    }

    @Query(
        value = """
        WITH membership_keys AS (
            SELECT user_id, scope_id
            FROM {h-schema}auth_user_roles
            WHERE scope_id > 0
            UNION
            SELECT u.id, k.key::BIGINT
            FROM {h-schema}auth_users u
            CROSS JOIN LATERAL JSONB_OBJECT_KEYS(u.scope_membership_versions) k(KEY)
        )
        SELECT COUNT(*)
        FROM membership_keys
        WHERE scope_id = :scopeId
            AND (CAST(:userId AS BIGINT) IS NULL OR user_id = :userId)
        """,
        nativeQuery = true
    )
    long memberCount(@Param("scopeId") long scopeId, @Param("userId") Long userId);

    @Query(
        value = """
        WITH membership_keys AS (
            SELECT user_id, scope_id
            FROM {h-schema}auth_user_roles
            WHERE scope_id > 0
            UNION
            SELECT u.id, k.key::BIGINT
            FROM {h-schema}auth_users u
            CROSS JOIN LATERAL JSONB_OBJECT_KEYS(u.scope_membership_versions) k(KEY)
        )
        SELECT m.scope_id, m.user_id, u.username, COALESCE(BOOL_AND(ur.active), FALSE) AS active,
            COALESCE((u.scope_membership_versions ->> m.scope_id::TEXT)::BIGINT, MAX(ur.version), 0) AS version,
            COALESCE(MAX(ur.updated_at), u.updated_at) AS updated_at, COALESCE(MAX(ur.updated_by), u.updated_by)
            AS updated_by, ARRAY_REMOVE(ARRAY_AGG(ur.role_code
                ORDER BY ur.role_code), NULL) AS role_codes
        FROM membership_keys m
        JOIN {h-schema}auth_users u ON u.id = m.user_id
        LEFT JOIN {h-schema}auth_user_roles ur ON ur.user_id = m.user_id AND ur.scope_id = m.scope_id
        WHERE m.scope_id = :scopeId
            AND (CAST(:userId AS BIGINT) IS NULL OR m.user_id = :userId)
        GROUP BY m.scope_id, m.user_id, u.id
        ORDER BY m.user_id
        LIMIT :pageSize OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> memberRows(
        @Param("scopeId") long scopeId,
        @Param("userId") Long userId,
        @Param("pageSize") int size,
        @Param("offset") long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<RoleDTO> members(long scope, UserSearch search) {
        long total = memberCount(scope, search.getUserId());
        List<RoleDTO> rows = memberRows(scope, search.getUserId(), search.getSize(), search.offset())
            .stream()
            .map(UserRoleRepo::member)
            .toList();
        return PageResponse.of(rows, search, total);
    }

    @Query(
        value = """
        WITH membership_keys AS (
            SELECT user_id, scope_id
            FROM {h-schema}auth_user_roles
            WHERE scope_id > 0
            UNION
            SELECT u.id, k.key::BIGINT
            FROM {h-schema}auth_users u
            CROSS JOIN LATERAL JSONB_OBJECT_KEYS(u.scope_membership_versions) k(KEY)
        )
        SELECT COUNT(*)
        FROM membership_keys
        WHERE user_id = :user
        """,
        nativeQuery = true
    )
    long membershipCount(@Param("user") long user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET scope_membership_versions = JSONB_SET(scope_membership_versions, ARRAY [CAST(:scope AS TEXT)],
                TO_JSONB(CAST(:nextVersion AS BIGINT)),
                TRUE), version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = :actor
        WHERE id = :user
        """,
        nativeQuery = true
    )
    int rememberMembership(
        @Param("user") long user,
        @Param("scope") long scope,
        @Param("nextVersion") long version,
        @Param("actor") long actor
    );

    /**
     * So version và giữ mốc trước batch thay mapping, kể cả tập role mới rỗng.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    default long prepareMembership(long scope, long user, long expectedVersion, long actor) {
        RoleDTO before = member(scope, user);
        long version = before == null ? 0 : before.getVersion();
        BusinessRules.require(expectedVersion == version, ErrorCode.STALE_VERSION);
        if (before == null) BusinessRules.require(membershipCount(user) < 32, ErrorCode.ACCESS_CAPACITY_REACHED, 32);
        rememberMembership(user, scope, version + 1, actor);
        return version;
    }

    @Query(
        value = """
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
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
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> mineRows(@Param("user") long user);

    @Transactional(readOnly = true)
    default List<RoleDTO> mine(long user) {
        return mineRows(user).stream().map(UserRoleRepo::scope).toList();
    }

    @Query(
        value = """
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
        nativeQuery = true
    )
    Map<String, Object> targetRow(
        @Param("user") long user,
        @Param("tokenVersion") long version,
        @Param("scope") long scope
    );

    @Transactional(readOnly = true)
    default Set<String> targets(long user, long tokenVersion, long scope) {
        Map<String, Object> row = targetRow(user, tokenVersion, scope);
        BusinessRules.require(row != null && !row.isEmpty(), ErrorCode.ACCESS_DENIED);
        return UserRepo.strings(row, "resource_targets");
    }

    @Query(
        value = """
        SELECT EXISTS (
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
        FROM {h-schema}carriers owner
        WHERE owner.scope_id = :scopeId
        """,
        nativeQuery = true
    )
    Boolean scopeAllowed(
        @Param("scopeId") long scopeId,
        @Param("viewerId") long user,
        @Param("viewerVersion") long version,
        @Param("viewTarget") String target
    );

    @Query(
        value = """
        SELECT EXISTS (
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
        )
        """,
        nativeQuery = true
    )
    boolean globalAllowed(
        @Param("viewerId") long user,
        @Param("viewerVersion") long version,
        @Param("viewTarget") String target
    );

    default void require(long scopeId) {
        UserPrincipal actor = UserPrincipal.current();
        BusinessRules.require(
            Boolean.TRUE.equals(scopeAllowed(scopeId, actor.id(), actor.tokenVersion(), actor.getTarget())),
            ErrorCode.ACCESS_DENIED
        );
    }

    default void requireGlobal(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(
            globalAllowed(actor.id(), actor.tokenVersion(), actor.getTarget()),
            ErrorCode.ACCESS_DENIED
        );
    }

    private static RoleDTO scope(Map<String, Object> row) {
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

    private static RoleDTO member(Map<String, Object> row) {
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

    static String visibleSql() {
        return VISIBLE;
    }

    /**
     * Chỉ đổi tên bind của endpoint đích, giữ nguyên toàn bộ predicate scope/role/function.
     */
    static String visibleSql(String targetParameter) {
        if (!Set.of("viewTarget", "updateTarget", "assignTarget").contains(targetParameter)) {
            throw new IllegalArgumentException("Tên tham số scope không hợp lệ");
        }
        return VISIBLE.replace(":viewTarget", ":" + targetParameter);
    }

    static void bindVisible(BiConsumer<String, Object> bind) {
        bindVisible(bind, UserPrincipal.current().getTarget(), "viewTarget");
    }

    /**
     * Khả năng UPDATE kiểm endpoint đích riêng, không dùng quyền GET của trang đang xem.
     */
    static void bindVisible(BiConsumer<String, Object> bind, String target) {
        bindVisible(bind, target, "updateTarget");
    }

    static void bindVisible(BiConsumer<String, Object> bind, String target, String targetParameter) {
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
        bind.accept("viewerId", actor.id());
        bind.accept("viewerVersion", actor.tokenVersion());
        bind.accept(targetParameter, target);
    }

    static void requireSameScope(long parentScope, long childScope) {
        if (parentScope < 1 || parentScope != childScope) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
