package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.repo.AccountRepository;
import vn.vietroute.auth.repo.DefaultAccountsRepository;
import vn.vietroute.auth.repo.DefaultAccountsRepository.NewAccount;
import vn.vietroute.auth.repo.UserRepository;

/** H2 in-memory kiểm JDBC thật/rollback cho SQL portable, không giả lập PostgreSQL migration hoặc scope guard. */
class AuthJdbcH2Test {

    private Connection connection;

    @BeforeEach
    void start() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:auth_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
        try (
            var input = getClass().getResourceAsStream("/auth/h2-jdbc.sql");
            var reader = new InputStreamReader(input, StandardCharsets.UTF_8)
        ) {
            RunScript.execute(connection, reader);
        }
        connection.setAutoCommit(false);
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void passwordShiftRevokeAndAuditCommitTogetherAndRollbackRestoresEveryColumn() throws Exception {
        execute(
            "INSERT INTO auth_users(id,username,display_name,password_hash,previous_password_hash_1,previous_password_hash_2,failed_login_attempts) VALUES(1,'passwordtest','Test','current','previous1','previous2',4)"
        );
        connection.commit();
        new UserRepository(null).changePassword(connection, 1, "next", 1);
        assertEquals("next,current,previous1,1,1,false,0", state());
        connection.rollback();
        assertEquals("current,previous1,previous2,0,0,true,4", state());
        new UserRepository(null).changePassword(connection, 1, "next", 1);
        connection.commit();
        new UserRepository(null).changePassword(connection, 1, "next2", 1);
        connection.commit();
        assertEquals("next2,next,current,2,2,false,0", state());
    }

    @Test
    void challengeIsSingleUsePurposeHashVersionBoundAndRollbackDoesNotLoseCode() throws Exception {
        execute("INSERT INTO auth_users(id,username,display_name,password_hash) VALUES(1,'challenge','Test','hash')");
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_account_challenges(user_id,purpose,token_hash,token_version,expires_at) VALUES(1,'RESET','hashed-code',7,?)"
            )
        ) {
            query.setObject(1, Instant.now().plusSeconds(300).atOffset(ZoneOffset.UTC));
            query.executeUpdate();
        }
        connection.commit();
        var accounts = new AccountRepository(null);
        assertFalse(accounts.consume(connection, 1, "ACTIVATE", "hashed-code", 7));
        assertFalse(accounts.consume(connection, 1, "RESET", "wrong-code", 7));
        assertFalse(accounts.consume(connection, 1, "RESET", "hashed-code", 6));
        assertTrue(accounts.consume(connection, 1, "RESET", "hashed-code", 7));
        assertFalse(accounts.consume(connection, 1, "RESET", "hashed-code", 7));
        connection.rollback();
        assertTrue(accounts.consume(connection, 1, "RESET", "hashed-code", 7));
        connection.commit();
        assertFalse(accounts.consume(connection, 1, "RESET", "hashed-code", 7));
    }

    @Test
    void expiredChallengeNeverConsumesAndInvalidationClearsAllPurposes() throws Exception {
        execute("INSERT INTO auth_users(id,username,display_name,password_hash) VALUES(1,'expired','Test','hash')");
        execute(
            "INSERT INTO auth_account_challenges VALUES(1,'RESET','expired',0,TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00+00'),(1,'ACTIVATE','activation',0,TIMESTAMP WITH TIME ZONE '2099-01-01 00:00:00+00')"
        );
        var accounts = new AccountRepository(null);
        assertFalse(accounts.consume(connection, 1, "RESET", "expired", 0));
        accounts.invalidate(connection, 1);
        assertEquals(0, count("auth_account_challenges"));
    }

    @Test
    void configuredAccountsAreVerifiedReadyAndRoleMappedUsingBatch() throws Exception {
        new DefaultAccountsRepository().insert(
            connection,
            List.of(
                new NewAccount("custom01", "Customer", "CUSTOMER", "customer@example.invalid", "test-hash-a"),
                new NewAccount("driver01", "Driver", "DRIVER", "driver@example.invalid", "test-hash-b"),
                new NewAccount("operator01", "Operator", "OPERATOR", "operator@example.invalid", "test-hash-c")
            )
        );
        connection.commit();
        assertEquals(3, count("auth_users"));
        assertEquals(3, count("auth_user_roles"));
        try (
            var query = connection.prepareStatement(
                "SELECT count(*) FROM auth_users WHERE email_verified_at IS NOT NULL AND active AND NOT activation_pending AND NOT must_change_password"
            );
            var row = query.executeQuery()
        ) {
            row.next();
            assertEquals(3, row.getInt(1));
        }
    }

    @Test
    void failedMappingBatchRollsBackAllAccountsAndKeepsExistingCredential() throws Exception {
        execute(
            "INSERT INTO auth_users(id,username,display_name,password_hash) VALUES(50,'existing','Existing','keep-this-hash')"
        );
        connection.commit();
        assertThrows(SQLException.class, () ->
            new DefaultAccountsRepository().insert(
                connection,
                List.of(
                    new NewAccount("first", "First", "DRIVER", "first@example.invalid", "a"),
                    new NewAccount("second", "Second", "MISSING", "second@example.invalid", "b")
                )
            )
        );
        connection.rollback();
        assertEquals(1, count("auth_users"));
        assertEquals(0, count("auth_user_roles"));
        try (
            var query = connection.prepareStatement("SELECT password_hash FROM auth_users WHERE id=50");
            var row = query.executeQuery()
        ) {
            row.next();
            assertEquals("keep-this-hash", row.getString(1));
        }
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String table) throws SQLException {
        try (var query = connection.prepareStatement("SELECT count(*) FROM " + table); var row = query.executeQuery()) {
            row.next();
            return row.getInt(1);
        }
    }

    private String state() throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT password_hash,previous_password_hash_1,previous_password_hash_2,version,token_version,must_change_password,failed_login_attempts FROM auth_users WHERE id=1"
            );
            var row = query.executeQuery()
        ) {
            row.next();
            return (
                row.getString(1) +
                "," +
                row.getString(2) +
                "," +
                row.getString(3) +
                "," +
                row.getLong(4) +
                "," +
                row.getLong(5) +
                "," +
                row.getBoolean(6) +
                "," +
                row.getInt(7)
            );
        }
    }
}
