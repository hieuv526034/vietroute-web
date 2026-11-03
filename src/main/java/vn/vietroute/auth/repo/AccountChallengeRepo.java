package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.AccountChallenge;
import vn.vietroute.common.base.JpaRepositorySupport;

/** Dọn/tiêu thụ mã trên transaction caller; không mở API đăng ký, email hoặc khôi phục mật khẩu. */
@Repository
public class AccountChallengeRepo extends JpaRepositorySupport<AccountChallenge, Long> {

    public AccountChallengeRepo(EntityManager manager) {
        super(AccountChallenge.class, manager);
    }

    /** Xóa đúng mã còn hạn, mục đích và phiên bản; một lần tiêu thụ, cùng rollback với caller. */
    public boolean consume(Connection connection, long user, String purpose, String hash, long version) {
        return (
            entityManager
                .createNativeQuery(
                    """
                    DELETE FROM {h-schema}auth_account_challenges
                    WHERE user_id = :userId AND purpose = :purpose AND token_hash = :hash AND token_version = :version
                        AND expires_at > CURRENT_TIMESTAMP
                    """
                )
                .setParameter("userId", user)
                .setParameter("purpose", purpose)
                .setParameter("hash", hash)
                .setParameter("version", version)
                .executeUpdate() == 1
        );
    }

    /** Đổi mật khẩu/thu hồi phiên phải xóa toàn bộ mã cũ trong cùng transaction. */
    public void invalidate(long user) {
        entityManager
            .createNativeQuery(
                """
                DELETE FROM {h-schema}auth_account_challenges
                WHERE user_id = :userId
                """
            )
            .setParameter("userId", user)
            .executeUpdate();
    }
}
