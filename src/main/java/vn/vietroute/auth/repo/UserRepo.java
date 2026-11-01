package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Repository bảng User; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class UserRepo extends JpaRepositorySupport<User, Long> {

    public UserRepo(EntityManager manager, JdbcTransactions transactions) {
        super(User.class, manager, transactions);
    }

    private static final String AUTHORITIES = """
    , ARRAY(SELECT r.code FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
        WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active ORDER BY r.code) AS role_codes
    , ARRAY(SELECT DISTINCT fr.target FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
        JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
        JOIN auth_function_resources fr ON fr.function_code=f.code WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active ORDER BY fr.target) AS resource_targets
    """;
    private static final String ARRAYS =
        AUTHORITIES +
        """
        , ARRAY(SELECT DISTINCT fr.target FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
        JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
        JOIN auth_function_resources fr ON fr.function_code=f.code WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active ORDER BY fr.target) AS global_targets
        """;

    static Set<String> roles(ResultSet row) throws SQLException {
        return strings(row, "role_codes");
    }

    static Set<String> targets(ResultSet row) throws SQLException {
        return strings(row, "resource_targets");
    }

    public static Set<String> strings(ResultSet row, String column) throws SQLException {
        Array array = row.getArray(column);
        if (array == null) return Set.of();
        try {
            return Set.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
    }

    private static final String ACCOUNT_COLUMNS =
        """
        u.id,u.username,u.display_name,u.email,u.avatar_media_id,u.email_verified_at,
        u.activation_pending,u.password_hash,u.active,u.must_change_password,
        u.failed_login_attempts,u.locked_until,u.token_version,u.version,
        u.created_at,u.updated_at,u.created_by,u.updated_by,
        (SELECT thumbnail_url FROM media_assets WHERE id=u.avatar_media_id AND status='READY') AS avatar_url
        """ + ARRAYS;

    /** Projection filter/me: một statement cho user + role/quyền hiện hành, không đọc password hash hoặc bộ đếm đăng nhập. */
    public User load(Long id) {
        return read(connection -> {
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT u.id,u.username,u.display_name,u.email,u.avatar_media_id,u.email_verified_at,
                           u.activation_pending,u.active,u.must_change_password,u.token_version,
                           u.version,u.created_at,u.updated_at,u.created_by,u.updated_by,
                           (SELECT thumbnail_url FROM media_assets WHERE id=u.avatar_media_id AND status='READY') AS avatar_url
                    """ +
                        ARRAYS +
                        " FROM auth_users u WHERE u.id=? AND NOT u.deleted"
                )
            ) {
                query.setLong(1, id);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) return null;
                    return User.builder()
                        .id(row.getLong("id"))
                        .username(row.getString("username"))
                        .displayName(row.getString("display_name"))
                        .email(row.getString("email"))
                        .avatarMediaId(row.getObject("avatar_media_id", Long.class))
                        .avatarUrl(row.getString("avatar_url"))
                        .emailVerifiedAt(instant(row, "email_verified_at"))
                        .activationPending(row.getBoolean("activation_pending"))
                        .active(row.getBoolean("active"))
                        .mustChangePassword(row.getBoolean("must_change_password"))
                        .tokenVersion(row.getLong("token_version"))
                        .version(row.getLong("version"))
                        .createdAt(instant(row, "created_at"))
                        .updatedAt(instant(row, "updated_at"))
                        .createdBy(row.getLong("created_by"))
                        .updatedBy(row.getLong("updated_by"))
                        .roles(roles(row))
                        .targets(targets(row))
                        .globalTargets(strings(row, "global_targets"))
                        .build();
                }
            }
        });
    }

    public User findByUsername(Connection connection, String username, boolean lock) throws SQLException {
        return find(connection, "username", username, lock);
    }

    /** Dùng connection của service; lock=true cho luồng thay đổi mật khẩu/phiên bản token. */
    public User load(Connection connection, Long id, boolean lock) throws SQLException {
        return find(connection, "id", id, lock);
    }

    private User find(Connection connection, String column, Object value, boolean lock) throws SQLException {
        // column chỉ do các hàm nội bộ chọn id/username, tuyệt đối không nhận tên cột từ HTTP.
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT " +
                    ACCOUNT_COLUMNS +
                    " FROM auth_users u WHERE u." +
                    column +
                    " = ? AND NOT u.deleted" +
                    (lock ? " FOR UPDATE OF u" : "")
            )
        ) {
            query.setObject(1, value);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return account(result);
            }
        }
    }

    /** Dùng cùng projection cho SELECT và UPDATE RETURNING, không query role từng lượt hoặc giữ SQL array ngoài JDBC. */
    private static User account(ResultSet result) throws SQLException {
        return User.builder()
            .id(result.getLong("id"))
            .username(result.getString("username"))
            .displayName(result.getString("display_name"))
            .email(result.getString("email"))
            .avatarMediaId(result.getObject("avatar_media_id", Long.class))
            .avatarUrl(result.getString("avatar_url"))
            .emailVerifiedAt(instant(result, "email_verified_at"))
            .activationPending(result.getBoolean("activation_pending"))
            .passwordHash(result.getString("password_hash"))
            .active(result.getBoolean("active"))
            .mustChangePassword(result.getBoolean("must_change_password"))
            .failedLoginAttempts(result.getInt("failed_login_attempts"))
            .lockedUntil(instant(result, "locked_until"))
            .tokenVersion(result.getLong("token_version"))
            .version(result.getLong("version"))
            .createdAt(instant(result, "created_at"))
            .updatedAt(instant(result, "updated_at"))
            .createdBy(result.getObject("created_by", Long.class))
            .updatedBy(result.getObject("updated_by", Long.class))
            .roles(roles(result))
            .targets(targets(result))
            .globalTargets(strings(result, "global_targets"))
            .build();
    }

    /** Ghi bộ đếm và thời điểm khóa trong transaction login; Instant chỉ đổi Timestamp tại biên JDBC. */
    public void recordFailure(Connection connection, long id, int attempts, Instant lockedUntil) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users SET failed_login_attempts=?, locked_until=?, updated_at=CURRENT_TIMESTAMP, updated_by=?, version=version+1 WHERE id=?"
            )
        ) {
            update.setInt(1, attempts);
            update.setTimestamp(2, lockedUntil == null ? null : Timestamp.from(lockedUntil));
            // Bộ đếm chống đăng nhập sai là quyết định của hệ thống, chưa có actor đã xác thực.
            update.setLong(3, SystemActor.ID);
            update.setLong(4, id);
            update.executeUpdate();
        }
    }

    /** Xóa bộ đếm/khóa và trả snapshot mới trong một statement; giữ nguyên audit, version và transaction login. */
    public User completeLogin(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users u SET failed_login_attempts=0,locked_until=NULL,updated_at=CURRENT_TIMESTAMP," +
                    "updated_by=?,version=version+1 WHERE u.id=? AND NOT u.deleted RETURNING " +
                    ACCOUNT_COLUMNS
            )
        ) {
            update.setLong(1, SystemActor.ID);
            update.setLong(2, id);
            try (ResultSet result = update.executeQuery()) {
                if (!result.next()) throw new SQLException(
                    "Không còn tài khoản đã khóa trong transaction login",
                    "02000"
                );
                return account(result);
            }
        }
    }

    /** Luồng cứu hộ chỉ xóa bộ đếm, không cần projection credential/quyền dùng để phát JWT đăng nhập. */
    public void resetFailures(Connection connection, long id) throws SQLException {
        recordFailure(connection, id, 0, null);
    }

    /** Hash đã được BCrypt ở service; tăng tokenVersion để vô hiệu hóa toàn bộ JWT cũ sau commit. */
    public void changePassword(Connection connection, long id, String hash) throws SQLException {
        changePassword(connection, id, hash, id);
    }

    /** Caller đã khóa user; lưu hash hiện tại rồi giữ đúng hai hash cũ trong cùng transaction đổi mật khẩu. */
    public void changePassword(Connection connection, long id, String hash, long actor) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users SET previous_password_hash_2=previous_password_hash_1,previous_password_hash_1=password_hash,password_hash=?, must_change_password=false, token_version=token_version+1, failed_login_attempts=0, locked_until=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
            )
        ) {
            update.setString(1, hash);
            update.setLong(2, actor);
            update.setLong(3, id);
            update.executeUpdate();
        }
    }

    /** Không so sánh chuỗi hash vì BCrypt có salt; đối chiếu cả hash hiện tại và hai hash cũ dưới cùng khóa user. */
    public void requireNewPassword(Connection connection, User user, String candidate, PasswordEncoder encoder)
        throws SQLException {
        BusinessRules.require(StrongPasswordValidator.valid(candidate), ErrorCode.VALIDATION_FAILED);
        BusinessRules.require(!encoder.matches(candidate, user.getPasswordHash()), ErrorCode.PASSWORD_REUSED);
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT hash FROM auth_users CROSS JOIN LATERAL unnest(ARRAY[previous_password_hash_1,previous_password_hash_2]) hash WHERE id=? AND hash IS NOT NULL"
            )
        ) {
            query.setLong(1, user.getId());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next())
                    BusinessRules.require(!encoder.matches(candidate, rows.getString(1)), ErrorCode.PASSWORD_REUSED);
            }
        }
    }

    /** Logout tăng version token; không cần lưu từng JWT hoặc danh sách token đã thu hồi. */
    public void revokeTokens(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users SET token_version=token_version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
            )
        ) {
            update.setLong(1, id);
            update.setLong(2, id);
            update.executeUpdate();
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** COUNT và trang dữ liệu cùng bộ lọc bind; không dùng N+1 để lấy role. */
    public PageResponse<UserDTO> search(UserSearch page) {
        return read(connection -> {
            String where = " FROM auth_users u WHERE NOT u.deleted AND u.id<>? AND lower(u.username) LIKE ? ESCAPE '!'";
            if (page.getRole() != null && !page.getRole().isBlank()) where +=
                " AND EXISTS(SELECT 1 FROM auth_user_roles ur WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.role_code=?)";
            if (page.getActive() != null) where += " AND u.active=?";
            long total;
            try (PreparedStatement count = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(count, page);
                try (ResultSet row = count.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            ArrayList<UserDTO> content = new ArrayList<UserDTO>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT u.id,u.username,u.display_name,u.email,u.email_verified_at,u.updated_by,u.active,u.activation_pending,u.must_change_password,(u.email_verified_at IS NULL) AS email_verification_required,u.locked_until,u.version,u.created_at,u.updated_at,COALESCE((SELECT string_agg(role_code,',' ORDER BY role_code) FROM auth_user_roles WHERE user_id=u.id AND scope_id=0),'') AS roles" +
                        where +
                        " ORDER BY u.username,u.id LIMIT ? OFFSET ?"
                )
            ) {
                int index = bind(query, page);
                query.setInt(index++, page.getSize());
                query.setLong(index, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String roles = rows.getString("roles");
                        Set<String> assigned = roles.isEmpty()
                            ? Set.of()
                            : Arrays.stream(roles.split(","))

                                  .collect(Collectors.toUnmodifiableSet());
                        UserDTO dto = new UserDTO();
                        dto.setId(rows.getLong("id"));
                        dto.setUsername(rows.getString("username"));
                        dto.setDisplayName(rows.getString("display_name"));
                        dto.setEmail(rows.getString("email"));
                        dto.setActive(rows.getBoolean("active"));
                        dto.setMustChangePassword(rows.getBoolean("must_change_password"));
                        dto.setActivationPending(rows.getBoolean("activation_pending"));
                        dto.setEmailVerifiedAt(instant(rows, "email_verified_at"));
                        dto.setEmailVerificationRequired(rows.getTimestamp("email_verified_at") == null);
                        dto.setRoles(assigned);
                        dto.setVersion(rows.getLong("version"));
                        dto.setUpdatedAt(instant(rows, "updated_at"));
                        dto.setUpdatedBy(rows.getLong("updated_by"));
                        content.add(dto);
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    private int bind(PreparedStatement query, UserSearch page) throws SQLException {
        query.setLong(1, SystemActor.ID);
        query.setString(
            2,
            page.getUsername().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        );
        int index = 3;
        if (page.getRole() != null && !page.getRole().isBlank()) query.setString(index++, page.getRole());
        if (page.getActive() != null) query.setBoolean(index++, page.getActive());
        return index;
    }

    /** Mã đã gán vẫn hiện khi role đang tắt; không dùng tập này để xây authorities. */

    public User lock(long id) {
        return entityManager.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public boolean existsUsername(String username) {
        return (
            entityManager
                .createQuery("select count(u) from User u where u.username=:name", Long.class)
                .setParameter("name", username)
                .getSingleResult() > 0
        );
    }

    public boolean existsEmail(String email) {
        return (
            email != null &&
            entityManager
                .createQuery("select count(u) from User u where u.email=:email and u.deleted=false", Long.class)
                .setParameter("email", email)
                .getSingleResult() > 0
        );
    }

    /** Cùng khóa với mọi thao tác gán quyền; nhiều node không tạo admin hoặc mapping trùng nhau. */
    public void lockBootstrap(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            query.execute();
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
        Long id = null;
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM auth_users WHERE username=? FOR UPDATE"
            )
        ) {
            query.setString(1, username);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) id = row.getLong(1);
            }
        }
        // Chỉ mã hóa khi tạo mới. Email do chủ hệ thống cấp sẵn, không suy luận đặc quyền từ role ADMIN.
        boolean created = id == null;
        if (created) try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_users(username,password_hash,display_name,created_by,updated_by,email,
                  email_verified_at,active,activation_pending,must_change_password)
                VALUES (?,?,'Quản trị viên',?,?,?,CURRENT_TIMESTAMP,true,false,false) RETURNING id
                """
            )
        ) {
            insert.setString(1, username);
            insert.setString(2, passwordHash.get());
            insert.setLong(3, actor);
            insert.setLong(4, actor);
            insert.setString(5, email);
            try (ResultSet row = insert.executeQuery()) {
                row.next();
                id = row.getLong(1);
            }
        }
        // Chỉ danh tính bootstrap được sửa về trạng thái sẵn sàng. Không xóa failed_attempts/locked_until,
        // không đổi hash/lịch sử mật khẩu, không cập nhật account khác dù account đó có role ADMIN.
        if (!created) try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE auth_users SET email=?,email_verified_at=CASE WHEN email IS DISTINCT FROM ?
                  OR email_verified_at IS NULL THEN CURRENT_TIMESTAMP ELSE email_verified_at END,
                  active=true,deleted=false,activation_pending=false,must_change_password=false,
                  token_version=token_version+1,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=?
                WHERE id=? AND (email IS DISTINCT FROM ? OR email_verified_at IS NULL
                  OR NOT active OR deleted OR activation_pending OR must_change_password)
                """
            )
        ) {
            update.setString(1, email);
            update.setString(2, email);
            update.setLong(3, actor);
            update.setLong(4, id);
            update.setString(5, email);
            if (update.executeUpdate() > 0) try (
                PreparedStatement invalidate = connection.prepareStatement(
                    "DELETE FROM auth_account_challenges WHERE user_id=?"
                )
            ) {
                // Mã reset/kích hoạt cũ không được tiếp tục dùng sau khi sửa email/trạng thái gốc.
                invalidate.setLong(1, id);
                invalidate.executeUpdate();
            }
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) VALUES (?,?,?,?)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            insert.setLong(1, id);
            insert.setString(2, bootstrapRole);
            insert.setLong(3, actor);
            insert.setLong(4, actor);
            if (insert.executeUpdate() > 0 && !created) try (
                PreparedStatement update = connection.prepareStatement(
                    """
                    UPDATE auth_users SET token_version=token_version+1,version=version+1,
                      updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?
                    """
                )
            ) {
                update.setLong(1, actor);
                update.setLong(2, id);
                update.executeUpdate();
            }
        }
        return id;
    }

    public void insertDefaults(Connection connection, List<User> accounts) throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_users(username,display_name,email,password_hash,email_verified_at,active,activation_pending,must_change_password,bootstrap_scope_pending) VALUES(?,?,?,?,CURRENT_TIMESTAMP,true,false,false,true) ON CONFLICT DO NOTHING"
            )
        ) {
            for (User account : accounts) {
                insert.setString(1, account.getUsername());
                insert.setString(2, account.getDisplayName());
                insert.setString(3, account.getEmail());
                insert.setString(4, account.getPasswordHash());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_user_roles(user_id,role_code) SELECT id,? FROM auth_users WHERE username=? AND bootstrap_scope_pending ON CONFLICT DO NOTHING"
            )
        ) {
            for (User account : accounts) {
                insert.setString(1, account.getRoles().iterator().next());
                insert.setString(2, account.getUsername());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public Set<String> existingUsernames(Connection connection, Set<String> names) throws SQLException {
        Array codes = connection.createArrayOf("text", names.toArray(String[]::new));
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT username FROM auth_users WHERE username=ANY(?)"
            )
        ) {
            query.setArray(1, codes);
            try (ResultSet rows = query.executeQuery()) {
                Set<String> result = new LinkedHashSet<>();
                while (rows.next()) result.add(rows.getString(1));
                return result;
            }
        } finally {
            codes.free();
        }
    }

    /** Chỉ account vừa init mới nhận scope mặc định một lần; không tự khôi phục mapping đã bị quản trị thu hồi. */
    public void assignInitialScopes(Connection connection) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id,role_code,scope_id)
                SELECT u.id,ur.role_code,c.scope_id FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.scope_id=0
                JOIN carriers c ON c.code='VRMIENNAM' WHERE u.bootstrap_scope_pending
                    AND (u.username='operator01' AND ur.role_code='OPERATOR' OR u.username='driver01' AND ur.role_code='DRIVER')
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE carrier_drivers d SET user_id=u.id,updated_at=CURRENT_TIMESTAMP,updated_by=0,version=d.version+1
                FROM auth_users u,carriers c WHERE u.username='driver01' AND u.bootstrap_scope_pending AND c.code='VRMIENNAM'
                    AND d.carrier_id=c.id AND d.code='DRVSOUTH01' AND d.user_id IS NULL
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "UPDATE auth_users SET bootstrap_scope_pending=false WHERE bootstrap_scope_pending AND (username='custom01' OR EXISTS(SELECT 1 FROM auth_user_roles ur WHERE ur.user_id=auth_users.id AND ur.scope_id>0))"
            )
        ) {
            query.executeUpdate();
        }
    }
}
