package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.AccountChallengeRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.repo.custom.UserCustomRepo;
import vn.vietroute.support.TestJpa;

/** H2 in-memory kiểm JDBC thật/rollback cho SQL portable, không giả lập PostgreSQL migration hoặc scope guard. */
class AuthJdbcH2Test {

    private Connection connection;
    private EntityManager manager;
    private LocalContainerEntityManagerFactoryBean factory;

    @BeforeEach
    void start() throws Exception {
        var source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:auth_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        source.setUser("sa");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("vn.vietroute.auth.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.default_schema", "public"));
        factory.afterPropertiesSet();
        manager = factory.getObject().createEntityManager();
        begin();
        try (
            var input = getClass().getResourceAsStream("/auth/h2-jdbc.sql");
            var reader = new InputStreamReader(input, StandardCharsets.UTF_8)
        ) {
            RunScript.execute(connection, reader);
        }
        commit();
    }

    @AfterEach
    void close() throws Exception {
        if (manager != null) {
            if (manager.getTransaction().isActive()) manager.getTransaction().rollback();
            manager.close();
        }
        if (factory != null) factory.destroy();
    }

    private void begin() {
        manager.getTransaction().begin();
        connection = manager.unwrap(Session.class).doReturningWork(value -> value);
    }

    private void commit() {
        manager.getTransaction().commit();
        manager.clear();
        begin();
    }

    private void rollback() {
        manager.getTransaction().rollback();
        manager.clear();
        begin();
    }

    @Test
    void passwordShiftRevokeAndAuditCommitTogetherAndRollbackRestoresEveryColumn() throws Exception {
        execute(
            "INSERT INTO auth_users(id,username,display_name,password_hash,previous_password_hash_1,previous_password_hash_2,failed_login_attempts) VALUES(1,'passwordtest','Test','current','previous1','previous2',4)"
        );
        commit();
        TestJpa.repository(UserRepo.class, manager).changePassword(1, "next", 1);
        assertEquals("next,current,previous1,1,1,false,0", state());
        rollback();
        assertEquals("current,previous1,previous2,0,0,true,4", state());
        TestJpa.repository(UserRepo.class, manager).changePassword(1, "next", 1);
        commit();
        TestJpa.repository(UserRepo.class, manager).changePassword(1, "next2", 1);
        commit();
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
        commit();
        var accounts = TestJpa.repository(AccountChallengeRepo.class, manager);
        assertFalse(accounts.consume(1, "ACTIVATE", "hashed-code", 7));
        assertFalse(accounts.consume(1, "RESET", "wrong-code", 7));
        assertFalse(accounts.consume(1, "RESET", "hashed-code", 6));
        assertTrue(accounts.consume(1, "RESET", "hashed-code", 7));
        assertFalse(accounts.consume(1, "RESET", "hashed-code", 7));
        rollback();
        assertTrue(accounts.consume(1, "RESET", "hashed-code", 7));
        commit();
        assertFalse(accounts.consume(1, "RESET", "hashed-code", 7));
    }

    @Test
    void expiredChallengeNeverConsumesAndInvalidationClearsAllPurposes() throws Exception {
        execute("INSERT INTO auth_users(id,username,display_name,password_hash) VALUES(1,'expired','Test','hash')");
        execute(
            "INSERT INTO auth_account_challenges VALUES(1,'RESET','expired',0,TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00+00'),(1,'ACTIVATE','activation',0,TIMESTAMP WITH TIME ZONE '2099-01-01 00:00:00+00')"
        );
        var accounts = TestJpa.repository(AccountChallengeRepo.class, manager);
        assertFalse(accounts.consume(1, "RESET", "expired", 0));
        accounts.invalidate(1);
        assertEquals(0, count("auth_account_challenges"));
    }

    @Test
    void configuredAccountsAreVerifiedReadyAndRoleMappedUsingBatch() throws Exception {
        new UserCustomRepo().insertDefaults(
            connection,
            List.of(
                account("custom01", "Customer", "CUSTOMER", "customer@example.invalid", "test-hash-a"),
                account("driver01", "Driver", "DRIVER", "driver@example.invalid", "test-hash-b"),
                account("operator01", "Operator", "OPERATOR", "operator@example.invalid", "test-hash-c")
            )
        );
        commit();
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
        commit();
        assertThrows(SQLException.class, () ->
            new UserCustomRepo().insertDefaults(
                connection,
                List.of(
                    account("first", "First", "DRIVER", "first@example.invalid", "a"),
                    account("second", "Second", "MISSING", "second@example.invalid", "b")
                )
            )
        );
        rollback();
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

    private static User account(String username, String name, String role, String email, String hash) {
        return User.builder()
            .username(username)
            .displayName(name)
            .roles(Set.of(role))
            .email(email)
            .passwordHash(hash)
            .build();
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
