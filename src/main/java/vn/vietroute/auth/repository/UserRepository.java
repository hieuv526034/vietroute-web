package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** SQL thuần cho auth; caller sở hữu connection khi thực hiện thay đổi trong transaction. */
@Repository
public class UserRepository extends BaseRepository {

    public UserRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Đọc độc lập cho filter/me; BaseRepository tự đóng connection sau khi map user và role. */
    public UserAccount findById(Long id) {
        return read(connection -> find(connection, "id", id, false));
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
                "SELECT * FROM vietroute.auth_users WHERE " + column + " = ?" + (lock ? " FOR UPDATE" : "")
            )
        ) {
            query.setObject(1, value);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                UserAccount user = UserAccount.builder()
                    .id(result.getLong("id"))
                    .username(result.getString("username"))
                    .displayName(result.getString("display_name"))
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
                    .build();
                user.setRoles(new LinkedHashSet<>());
                try (
                    PreparedStatement roles = connection.prepareStatement(
                        "SELECT role_code FROM vietroute.auth_user_roles WHERE user_id = ? ORDER BY role_code"
                    )
                ) {
                    roles.setLong(1, user.getId());
                    try (ResultSet values = roles.executeQuery()) {
                        while (values.next()) {
                            user.getRoles().add(Role.valueOf(values.getString(1)));
                        }
                    }
                }
                return user;
            }
        }
    }

    /** Ghi bộ đếm và thời điểm khóa trong transaction login; Instant chỉ đổi Timestamp tại biên JDBC. */
    public void recordFailure(Connection connection, long id, int attempts, Instant lockedUntil) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE vietroute.auth_users SET failed_login_attempts=?, locked_until=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
            )
        ) {
            update.setInt(1, attempts);
            update.setTimestamp(2, lockedUntil == null ? null : Timestamp.from(lockedUntil));
            update.setLong(3, id);
            update.executeUpdate();
        }
    }

    /** Thành công thì xóa bộ đếm/khóa cũ, dùng cùng câu update để giữ audit và version nhất quán. */
    public void resetFailures(Connection connection, long id) throws SQLException {
        recordFailure(connection, id, 0, null);
    }

    /** Hash đã được BCrypt ở service; tăng tokenVersion để vô hiệu hóa toàn bộ JWT cũ sau commit. */
    public void changePassword(Connection connection, long id, String hash) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE vietroute.auth_users SET password_hash=?, must_change_password=false, token_version=token_version+1, failed_login_attempts=0, locked_until=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
            )
        ) {
            update.setString(1, hash);
            update.setLong(2, id);
            update.setLong(3, id);
            update.executeUpdate();
        }
    }

    /** Logout tăng version token; không cần lưu từng JWT hoặc danh sách token đã thu hồi. */
    public void revokeTokens(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE vietroute.auth_users SET token_version=token_version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?"
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
