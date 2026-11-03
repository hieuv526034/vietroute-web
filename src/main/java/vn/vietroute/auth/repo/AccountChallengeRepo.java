package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.AccountChallenge;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng AccountChallenge; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class AccountChallengeRepo extends JpaRepositorySupport<AccountChallenge, Long> {

    public AccountChallengeRepo(EntityManager manager, JdbcTransactions transactions) {
        super(AccountChallenge.class, manager, transactions);
    }

    /** Serialize đăng ký/bind email giữa các node; không khóa toàn bộ request đăng nhập. */
    public void lockRegistration(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT PG_ADVISORY_XACT_LOCK(781205617)")) {
            query.execute();
        }
    }

    public Long findByEmail(Connection connection, String email) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement("SELECT id FROM auth_users WHERE email=? AND id<>?")
        ) {
            query.setString(1, email);
            query.setLong(2, SystemActor.ID);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
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
            PreparedStatement budget = connection.prepareStatement(
                """
                SELECT COUNT(*)
                FROM system_jobs
                WHERE type = 'AUTH_ACCOUNT_MAIL'
                    AND dedup_key LIKE ?
                    AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
                """
            )
        ) {
            budget.setString(1, "account-mail:" + user + ":%");
            try (ResultSet row = budget.executeQuery()) {
                row.next();
                if (row.getInt(1) >= 10) return false;
            }
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_account_challenges(user_id, purpose, token_hash, token_version, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, purpose)
                DO UPDATE
                SET token_hash = EXCLUDED.token_hash, token_version = EXCLUDED.token_version,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = CURRENT_TIMESTAMP
                WHERE auth_account_challenges.updated_at < CURRENT_TIMESTAMP - INTERVAL '60 seconds'
                """
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
            PreparedStatement query = connection.prepareStatement(
                """
                DELETE
                FROM auth_account_challenges
                WHERE user_id = ? AND purpose = ? AND token_hash = ? AND token_version = ?
                    AND expires_at > CURRENT_TIMESTAMP
                """
            )
        ) {
            query.setLong(1, user);
            query.setString(2, purpose);
            query.setString(3, hash);
            query.setLong(4, version);
            return query.executeUpdate() == 1;
        }
    }

    public void invalidate(Connection connection, long user) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement("DELETE FROM auth_account_challenges WHERE user_id=?")
        ) {
            query.setLong(1, user);
            query.executeUpdate();
        }
    }
}
