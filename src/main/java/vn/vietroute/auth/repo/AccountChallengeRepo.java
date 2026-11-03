package vn.vietroute.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.entity.AccountChallenge;

/** SQL native của AccountChallenge; transaction ghi do Service/Initializer sở hữu. */
public interface AccountChallengeRepo extends JpaRepository<AccountChallenge, Long> {
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        DELETE FROM {h-schema}auth_account_challenges
        WHERE user_id = :userId AND purpose = :purpose AND token_hash = :hash AND token_version = :version
            AND expires_at > CURRENT_TIMESTAMP
        """,
        nativeQuery = true
    )
    int consumeRow(
        @Param("userId") long user,
        @Param("purpose") String purpose,
        @Param("hash") String hash,
        @Param("version") long version
    );

    default boolean consume(long user, String purpose, String hash, long version) {
        return consumeRow(user, purpose, hash, version) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        DELETE FROM {h-schema}auth_account_challenges
        WHERE user_id = :userId
        """,
        nativeQuery = true
    )
    int invalidate(@Param("userId") long user);
}
