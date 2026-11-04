package vn.vietroute.auth.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** SQL native của User; transaction ghi do Service/Initializer sở hữu. */
public interface UserRepo extends JpaRepository<User, Long> {
    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.active, u.must_change_password, u.token_version, u.version, u.created_at, u.updated_at, u.created_by,
            u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        FROM {h-schema}auth_users u
        WHERE u.id = :id AND NOT u.deleted
        """,
        nativeQuery = true
    )
    Map<String, Object> sessionRow(@Param("id") Long id);

    /** Projection phiên đăng nhập chỉ đọc cột được phép, không lấy password hash. */
    @Transactional(readOnly = true)
    default User load(Long id) {
        Map<String, Object> row = sessionRow(id);
        return row == null || row.isEmpty() ? null : account(row);
    }

    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
            u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        FROM {h-schema}auth_users u
        WHERE u.username = :username AND NOT u.deleted
        """,
        nativeQuery = true
    )
    Map<String, Object> byUsername(@Param("username") String username);

    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
            u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        FROM {h-schema}auth_users u
        WHERE u.username = :username AND NOT u.deleted
        FOR UPDATE OF u
        """,
        nativeQuery = true
    )
    Map<String, Object> byUsernameLocked(@Param("username") String username);

    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
            u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        FROM {h-schema}auth_users u
        WHERE u.id = :id AND NOT u.deleted
        """,
        nativeQuery = true
    )
    Map<String, Object> accountRow(@Param("id") Long id);

    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
            u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        FROM {h-schema}auth_users u
        WHERE u.id = :id AND NOT u.deleted
        FOR UPDATE OF u
        """,
        nativeQuery = true
    )
    Map<String, Object> accountLockedRow(@Param("id") Long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default User findByUsername(String username, boolean lock) {
        Map<String, Object> row = lock ? byUsernameLocked(username) : byUsername(username);
        return row == null || row.isEmpty() ? null : account(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    default User load(Long id, boolean lock) {
        Map<String, Object> row = lock ? accountLockedRow(id) : accountRow(id);
        return row == null || row.isEmpty() ? null : account(row);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET failed_login_attempts = :attempts, locked_until = :lockedUntil, updated_at = CURRENT_TIMESTAMP, updated_by =
            :systemActor, version = version + 1
        WHERE id = :id
        """,
        nativeQuery = true
    )
    int recordFailureRow(
        @Param("id") long id,
        @Param("attempts") int attempts,
        @Param("lockedUntil") Instant lockedUntil,
        @Param("systemActor") long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default void recordFailure(long id, int attempts, Instant lockedUntil) {
        recordFailureRow(id, attempts, lockedUntil, SystemActor.ID);
    }

    @Query(
        value = """
        UPDATE {h-schema}auth_users u
        SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP, updated_by = :systemActor,
            version = version + 1
        WHERE u.id = :id AND NOT u.deleted
        RETURNING
            u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
            u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
            u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
        , ARRAY(
            SELECT r.code
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY r.code
        ) AS role_codes, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS resource_targets, ARRAY(
            SELECT DISTINCT fr.target
            FROM {h-schema}auth_user_roles ur
            JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
            JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
            JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
            JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
            WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
            ORDER BY fr.target
        ) AS global_targets
        """,
        nativeQuery = true
    )
    Map<String, Object> completeLoginRow(@Param("id") long id, @Param("systemActor") long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    default User completeLogin(long id) {
        Map<String, Object> row = completeLoginRow(id, SystemActor.ID);
        if (row == null || row.isEmpty()) throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
        return account(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    default void resetFailures(long id) {
        recordFailureRow(id, 0, null, SystemActor.ID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET previous_password_hash_2 = previous_password_hash_1, previous_password_hash_1 = password_hash,
            password_hash = :hash, must_change_password = FALSE, token_version = token_version + 1,
            failed_login_attempts = 0, locked_until = NULL, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = :id
        """,
        nativeQuery = true
    )
    int changePassword(@Param("id") long id, @Param("hash") String hash, @Param("actor") long actor);

    @Query(
        value = """
        SELECT hash
        FROM {h-schema}auth_users
        CROSS JOIN LATERAL UNNEST(ARRAY [previous_password_hash_1, previous_password_hash_2]) hash
        WHERE id = :userId AND hash IS NOT NULL
        """,
        nativeQuery = true
    )
    List<String> previousPasswords(@Param("userId") long userId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET token_version = token_version + 1, updated_by = :id, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :id
        """,
        nativeQuery = true
    )
    int revokeTokens(@Param("id") long id);

    @Query(
        value = """
        SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.updated_by, u.active,
            u.activation_pending,
            u.must_change_password, (u.email_verified_at IS NULL) AS email_verification_required, u.locked_until,
            u.version, u.created_at, u.updated_at, COALESCE((
                SELECT STRING_AGG(role_code, ','
                    ORDER BY role_code)
                FROM {h-schema}auth_user_roles
                WHERE user_id = u.id AND scope_id = 0
        ), '') AS roles
        FROM {h-schema}auth_users u
        WHERE NOT u.deleted AND u.id <> :systemId
            AND LOWER(u.username) LIKE :usernamePrefix ESCAPE '!'
            AND (CAST(:roleCode AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM {h-schema}auth_user_roles ur
                WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.role_code = :roleCode
        ))
            AND (CAST(:active AS BOOLEAN) IS NULL OR u.active = :active)
        ORDER BY u.username, u.id
        LIMIT :pageSize OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> searchRows(
        @Param("systemId") long systemId,
        @Param("usernamePrefix") String usernamePrefix,
        @Param("roleCode") String roleCode,
        @Param("active") Boolean active,
        @Param("pageSize") int size,
        @Param("offset") long offset
    );

    @Query(
        value = """
        SELECT COUNT(*)
        FROM {h-schema}auth_users u
        WHERE NOT u.deleted AND u.id <> :systemId
            AND LOWER(u.username) LIKE :usernamePrefix ESCAPE '!'
            AND (CAST(:roleCode AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM {h-schema}auth_user_roles ur
                WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.role_code = :roleCode
        ))
            AND (CAST(:active AS BOOLEAN) IS NULL OR u.active = :active)
        """,
        nativeQuery = true
    )
    long searchCount(
        @Param("systemId") long systemId,
        @Param("usernamePrefix") String usernamePrefix,
        @Param("roleCode") String roleCode,
        @Param("active") Boolean active
    );

    /** Một count và một trang, role lấy theo tập trong SQL. */
    @Transactional(readOnly = true)
    default PageResponse<UserDTO> search(UserSearch page) {
        String username =
            page.getUsername().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String role = page.getRole() == null || page.getRole().isBlank() ? null : page.getRole();
        long total = searchCount(SystemActor.ID, username, role, page.getActive());
        List<UserDTO> content = searchRows(
            SystemActor.ID,
            username,
            role,
            page.getActive(),
            page.getSize(),
            page.offset()
        )
            .stream()
            .map(UserRepo::summary)
            .toList();
        return PageResponse.of(content, page, total);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT u.* FROM {h-schema}auth_users u WHERE u.id = :id FOR UPDATE ", nativeQuery = true)
    User lock(@Param("id") long id);

    @Query(value = "SELECT COUNT(*) > 0 FROM {h-schema}auth_users WHERE username = :name ", nativeQuery = true)
    boolean existsUsername(@Param("name") String username);

    @Query(
        value = "SELECT COUNT(*) > 0 FROM {h-schema}auth_users WHERE email = :email AND NOT deleted ",
        nativeQuery = true
    )
    boolean existsEmail(@Param("email") String email);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT) ", nativeQuery = true)
    String lockBootstrap();

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT id FROM {h-schema}auth_users WHERE username = :username FOR UPDATE ", nativeQuery = true)
    Long bootstrapId(@Param("username") String username);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
        INSERT INTO {h-schema}auth_users(username, password_hash, display_name, created_by, updated_by,
            email, email_verified_at, active, activation_pending, must_change_password)
        VALUES (:username, :hash, 'Quản trị viên', :actor, :actor, :email, CURRENT_TIMESTAMP, TRUE, FALSE, FALSE)
        RETURNING id
        """,
        nativeQuery = true
    )
    long insertBootstrap(
        @Param("username") String username,
        @Param("email") String email,
        @Param("hash") String hash,
        @Param("actor") long actor
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET email = :email, email_verified_at = CASE
            WHEN email IS DISTINCT FROM :email OR email_verified_at IS NULL THEN CURRENT_TIMESTAMP
            ELSE email_verified_at
        END, active = TRUE, deleted = FALSE, activation_pending = FALSE, must_change_password = FALSE,
            token_version = token_version + 1, version = version + 1,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor
        WHERE id = :id
            AND (email IS DISTINCT FROM :email OR email_verified_at IS NULL OR NOT active OR deleted
                OR activation_pending OR must_change_password)
        """,
        nativeQuery = true
    )
    int repairBootstrap(@Param("id") long id, @Param("email") String email, @Param("actor") long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO {h-schema}auth_user_roles(user_id, role_code, created_by, updated_by)
        VALUES (:id, :role, :actor, :actor)
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true
    )
    int assignBootstrap(@Param("id") long id, @Param("role") String role, @Param("actor") long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET token_version = token_version + 1, version = version + 1,
            updated_at = CURRENT_TIMESTAMP, updated_by = :actor
        WHERE id = :id
        """,
        nativeQuery = true
    )
    int revokeBootstrapTokens(@Param("id") long id, @Param("actor") long actor);

    @Query(value = "SELECT username FROM {h-schema}auth_users WHERE username IN (:names) ", nativeQuery = true)
    Set<String> existingUsernames(@Param("names") Set<String> names);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}auth_users
        SET deleted = TRUE, active = FALSE, updated_by = :actor, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = :id
        """,
        nativeQuery = true
    )
    int softDelete(@Param("id") long id, @Param("actor") long actor);

    private static User account(Map<String, Object> result) {
        return User.builder()
            .id(longValue(result.get("id")))
            .username(stringValue(result.get("username")))
            .displayName(stringValue(result.get("display_name")))
            .email(stringValue(result.get("email")))
            .emailVerifiedAt(instant(result, "email_verified_at"))
            .activationPending(booleanValue(result.get("activation_pending")))
            .passwordHash(stringValue(result.get("password_hash")))
            .active(booleanValue(result.get("active")))
            .mustChangePassword(booleanValue(result.get("must_change_password")))
            .failedLoginAttempts(intValue(result.get("failed_login_attempts")))
            .lockedUntil(instant(result, "locked_until"))
            .tokenVersion(longValue(result.get("token_version")))
            .version(longValue(result.get("version")))
            .createdAt(instant(result, "created_at"))
            .updatedAt(instant(result, "updated_at"))
            .createdBy(longValue(result.get("created_by")))
            .updatedBy(longValue(result.get("updated_by")))
            .roles(roles(result))
            .targets(targets(result))
            .globalTargets(strings(result, "global_targets"))
            .build();
    }

    private static Instant instant(Map<String, Object> row, String key) {
        return instantValue(row.get(key));
    }

    static Set<String> strings(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) return Set.of();
        return Set.copyOf(Arrays.asList((String[]) value));
    }

    private static Set<String> roles(Map<String, Object> row) {
        return strings(row, "role_codes");
    }

    private static Set<String> targets(Map<String, Object> row) {
        return strings(row, "resource_targets");
    }

    private static UserDTO summary(Map<String, Object> row) {
        UserDTO dto = new UserDTO();
        dto.setId(longValue(row.get("id")));
        dto.setUsername(stringValue(row.get("username")));
        dto.setDisplayName(stringValue(row.get("display_name")));
        dto.setEmail(stringValue(row.get("email")));
        dto.setActive(booleanValue(row.get("active")));
        dto.setMustChangePassword(booleanValue(row.get("must_change_password")));
        dto.setActivationPending(booleanValue(row.get("activation_pending")));
        dto.setEmailVerifiedAt(instantValue(row.get("email_verified_at")));
        dto.setEmailVerificationRequired(row.get("email_verified_at") == null);
        String roles = stringValue(row.get("roles"));
        dto.setRoles(roles.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(roles.split(","))));
        dto.setVersion(longValue(row.get("version")));
        dto.setUpdatedAt(instantValue(row.get("updated_at")));
        dto.setUpdatedBy(longValue(row.get("updated_by")));
        return dto;
    }
}
