package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** SQL của đăng ký/challenge chỉ chạy trong transaction service; không gọi provider khi đang giữ khóa. */
@Repository
public class AccountRepository extends BaseRepository {

    public AccountRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Serialize đăng ký/bind email giữa các node; không khóa toàn bộ request đăng nhập. */
    public void lockRegistration(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205617)")) {
            query.execute();
        }
    }

    public Long findByEmail(Connection connection, String email) throws SQLException {
        try (var query = connection.prepareStatement("SELECT id FROM auth_users WHERE email=? AND id<>?")) {
            query.setString(1, email);
            query.setLong(2, SystemActor.ID);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
            }
        }
    }

    /** INSERT trả null khi username/email đã tồn tại; không thông báo phân biệt tài khoản cho anonymous. */
    public Long register(Connection connection, String username, String name, String email, String hash, int cap)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_users(username,display_name,email,password_hash,active,activation_pending,must_change_password,created_by,updated_by) " +
                    "SELECT ?,?,?,?,false,true,false,?,? WHERE (SELECT count(*) FROM auth_users WHERE email IS NOT NULL AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours')<? " +
                    "AND EXISTS (SELECT 1 FROM auth_roles WHERE code='CUSTOMER' AND active) ON CONFLICT DO NOTHING RETURNING id"
            )
        ) {
            query.setString(1, username);
            query.setString(2, name);
            query.setString(3, email);
            query.setString(4, hash);
            query.setLong(5, SystemActor.ID);
            query.setLong(6, SystemActor.ID);
            query.setInt(7, cap);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                long id = row.getLong(1);
                try (
                    var role = connection.prepareStatement(
                        "INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) VALUES (?,'CUSTOMER',?,?)"
                    )
                ) {
                    role.setLong(1, id);
                    role.setLong(2, SystemActor.ID);
                    role.setLong(3, SystemActor.ID);
                    role.executeUpdate();
                }
                return id;
            }
        }
    }

    /** Chỉ có một mã/mục đích và cooldown 60 giây, không gửi lại vô hạn theo cùng email. */
    public boolean challenge(
        Connection connection,
        long user,
        String purpose,
        String hash,
        long version,
        Instant expires
    ) throws SQLException {
        // Khóa user đã được service giữ: tối đa 10 email auth/tài khoản/24h trên toàn bộ node/mục đích.
        try (
            var budget = connection.prepareStatement(
                "SELECT count(*) FROM system_jobs WHERE type='AUTH_ACCOUNT_MAIL' AND dedup_key LIKE ? AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours'"
            )
        ) {
            budget.setString(1, "account-mail:" + user + ":%");
            try (ResultSet row = budget.executeQuery()) {
                row.next();
                if (row.getInt(1) >= 10) return false;
            }
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_account_challenges(user_id,purpose,token_hash,token_version,expires_at) VALUES (?,?,?,?,?) " +
                    "ON CONFLICT (user_id,purpose) DO UPDATE SET token_hash=EXCLUDED.token_hash,token_version=EXCLUDED.token_version," +
                    "expires_at=EXCLUDED.expires_at,updated_at=CURRENT_TIMESTAMP WHERE auth_account_challenges.updated_at<CURRENT_TIMESTAMP-INTERVAL '60 seconds'"
            )
        ) {
            query.setLong(1, user);
            query.setString(2, purpose);
            query.setString(3, hash);
            query.setLong(4, version);
            query.setTimestamp(5, Timestamp.from(expires));
            return query.executeUpdate() == 1;
        }
    }

    /** Dưới khóa user của service, DELETE có điều kiện tiêu thụ mã đúng một lần và chống replay/cross-purpose. */
    public boolean consume(Connection connection, long user, String purpose, String hash, long version)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "DELETE FROM auth_account_challenges WHERE user_id=? AND purpose=? AND token_hash=? AND token_version=? AND expires_at>CURRENT_TIMESTAMP"
            )
        ) {
            query.setLong(1, user);
            query.setString(2, purpose);
            query.setString(3, hash);
            query.setLong(4, version);
            return query.executeUpdate() == 1;
        }
    }

    public void bindEmail(Connection connection, long user, String email) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET email=?,email_verified_at=NULL,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?"
            )
        ) {
            query.setString(1, email);
            query.setLong(2, user);
            query.setLong(3, user);
            query.executeUpdate();
        }
        invalidate(connection, user);
    }

    public void activate(Connection connection, long user) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET email_verified_at=CURRENT_TIMESTAMP,active=CASE WHEN activation_pending THEN true ELSE active END,activation_pending=false,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?"
            )
        ) {
            query.setLong(1, SystemActor.ID);
            query.setLong(2, user);
            query.executeUpdate();
        }
    }

    public void invalidate(Connection connection, long user) throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM auth_account_challenges WHERE user_id=?")) {
            query.setLong(1, user);
            query.executeUpdate();
        }
    }
}
