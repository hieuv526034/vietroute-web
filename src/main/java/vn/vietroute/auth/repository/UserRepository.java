package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.record.account.AccessUserResponse;
import vn.vietroute.auth.dto.record.account.ProfileUpdateRequest;
import vn.vietroute.auth.dto.request.account.AccessUserSearchRequest;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** SQL thuần cho auth; caller sở hữu connection khi thực hiện thay đổi trong transaction. */
@Repository
public class UserRepository extends BaseRepository {

    private static final String ACCOUNT_COLUMNS =
        """
        u.id,u.username,u.display_name,u.email,u.avatar_media_id,u.email_verified_at,
        u.activation_pending,u.password_hash,u.active,u.must_change_password,
        u.failed_login_attempts,u.locked_until,u.token_version,u.version,
        u.created_at,u.updated_at,u.created_by,u.updated_by,
        (SELECT thumbnail_url FROM media_assets WHERE id=u.avatar_media_id AND status='READY') AS avatar_url
        """ + AccountAccessSql.AUTHORITIES;

    public UserRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Projection filter/me: một statement cho user + role/quyền hiện hành, không đọc password hash hoặc bộ đếm đăng nhập. */
    public UserAccount findById(Long id) {
        return read(connection -> {
            try (
                var query = connection.prepareStatement(
                    """
                    SELECT u.id,u.username,u.display_name,u.email,u.avatar_media_id,u.email_verified_at,
                           u.activation_pending,u.active,u.must_change_password,u.token_version,
                           u.version,u.created_at,u.updated_at,u.created_by,u.updated_by,
                           (SELECT thumbnail_url FROM media_assets WHERE id=u.avatar_media_id AND status='READY') AS avatar_url
                    """ +
                        AccountAccessSql.AUTHORITIES +
                        " FROM auth_users u WHERE u.id=? AND NOT u.deleted"
                )
            ) {
                query.setLong(1, id);
                try (var row = query.executeQuery()) {
                    if (!row.next()) return null;
                    return UserAccount.builder()
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
                        .roles(AccountAccessSql.roles(row))
                        .permissions(AccountAccessSql.permissions(row))
                        .build();
                }
            }
        });
    }

    /** Chọn tường minh cột công khai, lấy role trong cùng query để không N+1 hoặc đọc password hash. */
    public PageResponse<AccessUserResponse> pageForAccess(AccessUserSearchRequest page) {
        return read(connection -> {
            String prefix = page.getUsername().toLowerCase(Locale.ROOT) + "%";
            long total;
            try (
                PreparedStatement count = connection.prepareStatement(
                    "SELECT count(*) FROM auth_users WHERE NOT deleted AND lower(username) LIKE ? AND id<>?"
                )
            ) {
                count.setString(1, prefix);
                count.setLong(2, SystemActor.ID);
                try (ResultSet row = count.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var content = new ArrayList<AccessUserResponse>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT u.id,u.username,u.display_name,u.active,u.version,u.created_at,u.updated_at," +
                        "COALESCE((SELECT string_agg(r.role_code,',' ORDER BY r.role_code) FROM auth_user_roles r WHERE r.user_id=u.id),'') AS roles " +
                        "FROM auth_users u WHERE NOT u.deleted AND lower(u.username) LIKE ? AND u.id<>? ORDER BY u.username,u.id LIMIT ? OFFSET ?"
                )
            ) {
                query.setString(1, prefix);
                query.setLong(2, SystemActor.ID);
                query.setInt(3, page.getSize());
                query.setLong(4, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String roleCodes = rows.getString("roles");
                        Set<Role> roles = roleCodes.isEmpty()
                            ? Set.of()
                            : Arrays.stream(roleCodes.split(","))
                                  .map(Role::valueOf)
                                  .collect(Collectors.toUnmodifiableSet());
                        content.add(
                            new AccessUserResponse(
                                rows.getLong("id"),
                                rows.getString("username"),
                                rows.getString("display_name"),
                                rows.getBoolean("active"),
                                roles,
                                rows.getLong("version"),
                                instant(rows, "created_at"),
                                instant(rows, "updated_at")
                            )
                        );
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    /** Chỉ đổi tên và liên kết ảnh của principal; không thay email, role, hash hoặc tokenVersion. */
    public void updateProfile(Connection connection, long id, ProfileUpdateRequest body) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET display_name=?,avatar_media_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?"
            )
        ) {
            query.setString(1, body.displayName().strip());
            query.setObject(2, body.avatarMediaId());
            query.setLong(3, id);
            query.setLong(4, id);
            query.setLong(5, body.version());
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Login truyền lock=true để FOR UPDATE bảo vệ bộ đếm sai khi nhiều request cùng tài khoản. */
    public UserAccount findByUsername(Connection connection, String username, boolean lock) throws SQLException {
        return find(connection, "username", username, lock);
    }

    /** Dùng connection của service; lock=true cho luồng thay đổi mật khẩu/phiên bản token. */
    public UserAccount findById(Connection connection, Long id, boolean lock) throws SQLException {
        return find(connection, "id", id, lock);
    }

    private UserAccount find(Connection connection, String column, Object value, boolean lock) throws SQLException {
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
    private static UserAccount account(ResultSet result) throws SQLException {
        return UserAccount.builder()
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
            .roles(AccountAccessSql.roles(result))
            .permissions(AccountAccessSql.permissions(result))
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
    public UserAccount completeLogin(Connection connection, long id) throws SQLException {
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
            var history = connection.prepareStatement(
                "INSERT INTO auth_password_history(user_id,password_hash,created_by,updated_by) SELECT id,password_hash,?,? FROM auth_users WHERE id=?"
            )
        ) {
            history.setLong(1, actor);
            history.setLong(2, actor);
            history.setLong(3, id);
            history.executeUpdate();
        }
        try (
            var prune = connection.prepareStatement(
                "DELETE FROM auth_password_history WHERE user_id=? AND id NOT IN (SELECT id FROM auth_password_history WHERE user_id=? ORDER BY id DESC LIMIT 2)"
            )
        ) {
            prune.setLong(1, id);
            prune.setLong(2, id);
            prune.executeUpdate();
        }
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users SET password_hash=?, must_change_password=false, token_version=token_version+1, failed_login_attempts=0, locked_until=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
            )
        ) {
            update.setString(1, hash);
            update.setLong(2, actor);
            update.setLong(3, id);
            update.executeUpdate();
        }
    }

    /** Không so sánh chuỗi hash vì BCrypt có salt; đối chiếu cả hash hiện tại và hai hash cũ dưới cùng khóa user. */
    public void requireNewPassword(Connection connection, UserAccount user, String candidate, PasswordEncoder encoder)
        throws SQLException {
        BusinessRules.require(StrongPasswordValidator.valid(candidate), ErrorCode.VALIDATION_FAILED);
        BusinessRules.require(!encoder.matches(candidate, user.getPasswordHash()), ErrorCode.PASSWORD_REUSED);
        try (
            var query = connection.prepareStatement(
                "SELECT password_hash FROM auth_password_history WHERE user_id=? ORDER BY id DESC LIMIT 2"
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
}
