package vn.vietroute.auth.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Repository bảng User; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class UserRepo extends JpaRepositorySupport<User, Long> {

    public UserRepo(EntityManager manager) {
        super(User.class, manager);
    }

    private static final String AUTHORITIES = """
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
    ) AS resource_targets
    """;
    private static final String ARRAYS = """
    %s, ARRAY(
        SELECT DISTINCT fr.target
        FROM {h-schema}auth_user_roles ur
        JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
        JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
        JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
        JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
        WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
        ORDER BY fr.target
    ) AS global_targets
    """.formatted(AUTHORITIES);

    static Set<String> roles(Tuple row) {
        return strings(row, "role_codes");
    }

    static Set<String> targets(Tuple row) {
        return strings(row, "resource_targets");
    }

    public static Set<String> strings(Tuple row, String column) {
        String[] array = row.get(column, String[].class);
        return array == null ? Set.of() : Set.copyOf(Arrays.asList(array));
    }

    private static final String ACCOUNT_COLUMNS = """
    u.id, u.username, u.display_name, u.email, u.email_verified_at, u.activation_pending,
        u.password_hash, u.active, u.must_change_password, u.failed_login_attempts, u.locked_until,
        u.token_version, u.version, u.created_at, u.updated_at, u.created_by, u.updated_by
    %s
    """.formatted(ARRAYS);

    /** Projection filter/me: một statement cho user + role/quyền hiện hành, không đọc password hash hoặc bộ đếm đăng nhập. */
    @Transactional(readOnly = true)
    public User load(Long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at,
                    u.activation_pending,
                    u.active, u.must_change_password, u.token_version, u.version, u.created_at, u.updated_at,
                        u.created_by,
                    u.updated_by
                %s
                FROM {h-schema}auth_users u
                WHERE u.id = :id AND NOT u.deleted
                """.formatted(ARRAYS),
                Tuple.class
            );

            query.setParameter("id", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) return null;
                Tuple row = (Tuple) rowValues.get(0);

                return User.builder()
                    .id(longValue(row.get("id")))
                    .username(stringValue(row.get("username")))
                    .displayName(stringValue(row.get("display_name")))
                    .email(stringValue(row.get("email")))
                    .emailVerifiedAt(instant(row, "email_verified_at"))
                    .activationPending(booleanValue(row.get("activation_pending")))
                    .active(booleanValue(row.get("active")))
                    .mustChangePassword(booleanValue(row.get("must_change_password")))
                    .tokenVersion(longValue(row.get("token_version")))
                    .version(longValue(row.get("version")))
                    .createdAt(instant(row, "created_at"))
                    .updatedAt(instant(row, "updated_at"))
                    .createdBy(longValue(row.get("created_by")))
                    .updatedBy(longValue(row.get("updated_by")))
                    .roles(roles(row))
                    .targets(targets(row))
                    .globalTargets(strings(row, "global_targets"))
                    .build();
            }
        }
    }

    public User findByUsername(String username, boolean lock) {
        return find("username", username, lock);
    }

    /** Dùng connection của service; lock=true cho luồng thay đổi mật khẩu/phiên bản token. */
    public User load(Long id, boolean lock) {
        return find("id", id, lock);
    }

    private User find(String column, Object value, boolean lock) {
        // column chỉ do các hàm nội bộ chọn id/username, tuyệt đối không nhận tên cột từ HTTP.
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT %s
                FROM {h-schema}auth_users u
                WHERE u.%s = :value AND NOT u.deleted
                %s
                """.formatted(ACCOUNT_COLUMNS, column, lock ? " FOR UPDATE OF u" : ""),
                Tuple.class
            );

            query.setParameter("value", value);
            {
                List<?> resultValues = query.getResultList();

                if (resultValues.isEmpty()) {
                    return null;
                }
                Tuple result = (Tuple) resultValues.get(0);

                return account(result);
            }
        }
    }

    /** Dùng cùng projection cho SELECT và UPDATE RETURNING, không query role từng lượt hoặc giữ SQL array ngoài JDBC. */
    private static User account(Tuple result) {
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
            .createdBy(result.get("created_by", Long.class))
            .updatedBy(result.get("updated_by", Long.class))
            .roles(roles(result))
            .targets(targets(result))
            .globalTargets(strings(result, "global_targets"))
            .build();
    }

    /** Ghi bộ đếm và thời điểm khóa trong transaction login; Instant chỉ đổi Timestamp tại biên JDBC. */
    public void recordFailure(long id, int attempts, Instant lockedUntil) {
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users
                SET failed_login_attempts = :attempts, locked_until = :lockedUntil, updated_at = CURRENT_TIMESTAMP, updated_by = :systemActor,
                    version = version + 1
                WHERE id = :id
                """
            );

            update.setParameter("attempts", attempts);
            update.setParameter("lockedUntil", lockedUntil == null ? null : Timestamp.from(lockedUntil));
            // Bộ đếm chống đăng nhập sai là quyết định của hệ thống, chưa có actor đã xác thực.
            update.setParameter("systemActor", SystemActor.ID);
            update.setParameter("id", id);
            update.executeUpdate();
        }
    }

    /** Xóa bộ đếm/khóa và trả snapshot mới trong một statement; giữ nguyên audit, version và transaction login. */
    public User completeLogin(long id) {
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users u
                SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP, updated_by = :systemActor,
                    version = version + 1
                WHERE u.id = :id AND NOT u.deleted
                RETURNING
                %s
                """.formatted(ACCOUNT_COLUMNS),
                Tuple.class
            );

            update.setParameter("systemActor", SystemActor.ID);
            update.setParameter("id", id);
            {
                List<?> resultValues = update.getResultList();

                if (resultValues.isEmpty()) throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
                Tuple result = (Tuple) resultValues.get(0);

                return account(result);
            }
        }
    }

    /** Luồng cứu hộ chỉ xóa bộ đếm, không cần projection credential/quyền dùng để phát JWT đăng nhập. */
    public void resetFailures(long id) {
        recordFailure(id, 0, null);
    }

    /** Hash đã được BCrypt ở service; tăng tokenVersion để vô hiệu hóa toàn bộ JWT cũ sau commit. */
    public void changePassword(long id, String hash) {
        changePassword(id, hash, id);
    }

    /** Caller đã khóa user; lưu hash hiện tại rồi giữ đúng hai hash cũ trong cùng transaction đổi mật khẩu. */
    public void changePassword(long id, String hash, long actor) {
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users
                SET previous_password_hash_2 = previous_password_hash_1, previous_password_hash_1 = password_hash,
                    password_hash = :hash, must_change_password = FALSE, token_version = token_version + 1,
                    failed_login_attempts = 0, locked_until = NULL, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :id
                """
            );

            update.setParameter("hash", hash);
            update.setParameter("actor", actor);
            update.setParameter("id", id);
            update.executeUpdate();
        }
    }

    /** Không so sánh chuỗi hash vì BCrypt có salt; đối chiếu cả hash hiện tại và hai hash cũ dưới cùng khóa user. */
    public void requireNewPassword(User user, String candidate, PasswordEncoder encoder) {
        BusinessRules.require(BusinessRules.validPassword(candidate), ErrorCode.VALIDATION_FAILED);
        BusinessRules.require(!encoder.matches(candidate, user.getPasswordHash()), ErrorCode.PASSWORD_REUSED);
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT hash
                FROM {h-schema}auth_users
                CROSS JOIN LATERAL UNNEST(ARRAY [previous_password_hash_1, previous_password_hash_2]) hash
                WHERE id = :userId AND hash IS NOT NULL
                """,
                Tuple.class
            );

            query.setParameter("userId", user.getId());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    BusinessRules.require(
                        !encoder.matches(candidate, stringValue(rows.get(0))),
                        ErrorCode.PASSWORD_REUSED
                    );
                }
            }
        }
    }

    /** Logout tăng version token; không cần lưu từng JWT hoặc danh sách token đã thu hồi. */
    public void revokeTokens(long id) {
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_users
                SET token_version = token_version + 1, updated_by = :id, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :id
                """
            );

            update.setParameter("id", id);
            update.setParameter("id", id);
            update.executeUpdate();
        }
    }

    private static Instant instant(Tuple result, String column) {
        return instantValue(result.get(column));
    }

    /** COUNT và trang dữ liệu cùng bộ lọc bind; không dùng N+1 để lấy role. */
    @Transactional(readOnly = true)
    public PageResponse<UserDTO> search(UserSearch page) {
        String where =
            " FROM {h-schema}auth_users u WHERE NOT u.deleted AND u.id<>:systemId AND LOWER(u.username) LIKE :usernamePrefix ESCAPE '!'";
        if (page.getRole() != null && !page.getRole().isBlank()) where += """
            AND EXISTS (
                SELECT 1
                FROM {h-schema}auth_user_roles ur
                WHERE ur.user_id = u.id AND ur.scope_id = 0 AND ur.role_code = :roleCode
            )
        """;
        if (page.getActive() != null) where += " AND u.active=:active";
        long total;
        {
            Query count = entityManager.createNativeQuery("SELECT COUNT(*)" + where, Tuple.class);

            bind(count, page);
            {
                Tuple row = (Tuple) count.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<UserDTO> content = new ArrayList<UserDTO>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT u.id, u.username, u.display_name, u.email, u.email_verified_at, u.updated_by, u.active,
                    u.activation_pending,
                    u.must_change_password, (u.email_verified_at IS NULL) AS email_verification_required,
                        u.locked_until,
                    u.version, u.created_at, u.updated_at, COALESCE((
                        SELECT STRING_AGG(role_code, ','
                        ORDER BY role_code)
                        FROM {h-schema}auth_user_roles
                        WHERE user_id = u.id AND scope_id = 0
                    ), '') AS roles
                %s
                ORDER BY u.username, u.id
                LIMIT :pageSize OFFSET :offset
                """.formatted(where),
                Tuple.class
            );

            bind(query, page);
            query.setParameter("pageSize", page.getSize());
            query.setParameter("offset", page.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    String roles = stringValue(rows.get("roles"));
                    Set<String> assigned = roles.isEmpty()
                        ? Set.of()
                        : Arrays.stream(roles.split(","))

                              .collect(Collectors.toUnmodifiableSet());
                    UserDTO dto = new UserDTO();
                    dto.setId(longValue(rows.get("id")));
                    dto.setUsername(stringValue(rows.get("username")));
                    dto.setDisplayName(stringValue(rows.get("display_name")));
                    dto.setEmail(stringValue(rows.get("email")));
                    dto.setActive(booleanValue(rows.get("active")));
                    dto.setMustChangePassword(booleanValue(rows.get("must_change_password")));
                    dto.setActivationPending(booleanValue(rows.get("activation_pending")));
                    dto.setEmailVerifiedAt(instant(rows, "email_verified_at"));
                    dto.setEmailVerificationRequired(instantValue(rows.get("email_verified_at")) == null);
                    dto.setRoles(assigned);
                    dto.setVersion(longValue(rows.get("version")));
                    dto.setUpdatedAt(instant(rows, "updated_at"));
                    dto.setUpdatedBy(longValue(rows.get("updated_by")));
                    content.add(dto);
                }
            }
        }
        return PageResponse.of(content, page, total);
    }

    private void bind(Query query, UserSearch page) {
        query.setParameter("systemId", SystemActor.ID);
        query.setParameter(
            "usernamePrefix",
            page.getUsername().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        );
        if (page.getRole() != null && !page.getRole().isBlank()) query.setParameter("roleCode", page.getRole());
        if (page.getActive() != null) query.setParameter("active", page.getActive());
    }

    /** Mã đã gán vẫn hiện khi role đang tắt; không dùng tập này để xây authorities. */

    public User lock(long id) {
        return entityManager.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public boolean existsUsername(String username) {
        return (
            longValue(
                entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM {h-schema}auth_users WHERE username = :name")
                    .setParameter("name", username)
                    .getSingleResult()
            ) > 0
        );
    }

    public boolean existsEmail(String email) {
        return (
            email != null &&
            longValue(
                entityManager
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM {h-schema}auth_users WHERE email = :email AND deleted = FALSE"
                    )
                    .setParameter("email", email)
                    .getSingleResult()
            ) > 0
        );
    }

    /** Cùng khóa với mọi thao tác gán quyền; nhiều node không tạo admin hoặc mapping trùng nhau. */
    public void lockBootstrap(Connection connection) throws SQLException {
        {
            Query query = entityManager.createNativeQuery("SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT)");

            query.getSingleResult();
        }
    }

    /** Chỉ sửa role lõi và quyền khai báo bởi source; giữ trạng thái/mapping của các role thường. */
    public long initializeBootstrap(
        Connection connection,
        String username,
        String email,
        Supplier<String> passwordHash,
        long actor,
        String bootstrapRole
    ) throws SQLException {
        lockBootstrap(connection);
        List<?> matches = entityManager
            .createNativeQuery(
                """
                SELECT id FROM {h-schema}auth_users
                WHERE username = :username
                FOR UPDATE
                """
            )
            .setParameter("username", username)
            .getResultList();
        Long id = matches.isEmpty() ? null : ((Number) matches.get(0)).longValue();
        boolean created = id == null;
        // Chỉ mã hóa khi tạo mới; giữ hash/lịch sử/khóa sai của tài khoản đã có.
        if (created) {
            id = (
                (Number) entityManager
                    .createNativeQuery(
                        """
                        INSERT INTO {h-schema}auth_users(username, password_hash, display_name, created_by, updated_by,
                            email, email_verified_at, active, activation_pending, must_change_password)
                        VALUES (:username, :hash, 'Quản trị viên', :actor, :actor, :email,
                            CURRENT_TIMESTAMP, TRUE, FALSE, FALSE)
                        RETURNING id
                        """
                    )
                    .setParameter("username", username)
                    .setParameter("hash", passwordHash.get())
                    .setParameter("actor", actor)
                    .setParameter("email", email)
                    .getSingleResult()
            ).longValue();
        } else {
            int repaired = entityManager
                .createNativeQuery(
                    """
                    UPDATE {h-schema}auth_users
                    SET email = :email,
                        email_verified_at = CASE
                            WHEN email IS DISTINCT FROM :email OR email_verified_at IS NULL THEN CURRENT_TIMESTAMP
                            ELSE email_verified_at
                        END,
                        active = TRUE, deleted = FALSE, activation_pending = FALSE, must_change_password = FALSE,
                        token_version = token_version + 1, version = version + 1,
                        updated_at = CURRENT_TIMESTAMP, updated_by = :actor
                    WHERE id = :id
                        AND (email IS DISTINCT FROM :email OR email_verified_at IS NULL OR NOT active OR deleted
                            OR activation_pending OR must_change_password)
                    """
                )
                .setParameter("email", email)
                .setParameter("actor", actor)
                .setParameter("id", id)
                .executeUpdate();
            if (repaired > 0) {
                entityManager
                    .createNativeQuery(
                        """
                        DELETE FROM {h-schema}auth_account_challenges
                        WHERE user_id = :id
                        """
                    )
                    .setParameter("id", id)
                    .executeUpdate();
            }
        }
        int assigned = entityManager
            .createNativeQuery(
                """
                INSERT INTO {h-schema}auth_user_roles(user_id, role_code, created_by, updated_by)
                VALUES (:id, :role, :actor, :actor)
                ON CONFLICT DO NOTHING
                """
            )
            .setParameter("id", id)
            .setParameter("role", bootstrapRole)
            .setParameter("actor", actor)
            .executeUpdate();
        if (assigned > 0 && !created) {
            entityManager
                .createNativeQuery(
                    """
                    UPDATE {h-schema}auth_users
                    SET token_version = token_version + 1, version = version + 1,
                        updated_at = CURRENT_TIMESTAMP, updated_by = :actor
                    WHERE id = :id
                    """
                )
                .setParameter("actor", actor)
                .setParameter("id", id)
                .executeUpdate();
        }
        return id;
    }

    public Set<String> existingUsernames(Connection connection, Set<String> names) throws SQLException {
        String[] codes = names.toArray(String[]::new);
        {
            Query query = entityManager.createNativeQuery(
                "SELECT username FROM {h-schema}auth_users WHERE username=ANY(:codes)",
                Tuple.class
            );

            query.setParameter("codes", codes);
            {
                Set<String> result = new LinkedHashSet<>();
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    result.add(stringValue(rows.get(0)));
                }
                return result;
            }
        }
    }
}
