package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** H2 kiểm JPA thật + JDBC cùng transaction; migration/array/scope vẫn phải kiểm PostgreSQL riêng. */
class AuthJpaH2Test {

    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManagerFactory emf;
    private UserRepo users;
    private JdbcTransactions transactions;

    @BeforeEach
    void start() {
        var source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:jpa_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        source.setUser("sa");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("vn.vietroute.auth.entity");
        factory.setJpaPropertyMap(
            Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.dialect", "org.hibernate.dialect.H2Dialect")
        );
        factory.afterPropertiesSet();
        emf = factory.getObject();
        var shared = SharedEntityManagerCreator.createSharedEntityManager(emf);

        var manager = new JpaTransactionManager(emf);
        manager.setDataSource(source);
        var beans = new StaticListableBeanFactory();
        beans.addBean("transactionManager", manager);
        transactions = new JdbcTransactions(
            new JdbcConnectionFactory(new TransactionAwareDataSourceProxy(source), new JdbcSchema("h2_auth")),
            beans.getBeanProvider(PlatformTransactionManager.class)
        );
        users = new UserRepo(shared, transactions);
    }

    @AfterEach
    void close() {
        if (factory != null) factory.destroy();
    }

    private User user(String username) {
        var value = new User();
        value.setUsername(username);
        value.setDisplayName("Nguyễn Văn An");
        value.setPasswordHash("test-only-hash");
        value.setEmail(username + "@example.invalid");
        value.setEmailVerifiedAt(Instant.now());
        value.setActive(true);
        value.setCreatedAt(Instant.now());
        value.setUpdatedAt(value.getCreatedAt());
        value.setCreatedBy(0L);
        value.setUpdatedBy(0L);
        value.setPreviousPasswordHash1("previous-one");
        value.setPreviousPasswordHash2("previous-two");
        return value;
    }

    @Test
    void crudMapsAllCredentialAuditAndJsonColumnsWithoutExposingThemToDto() {
        long id = transactions.execute(connection -> users.saveAndFlush(user("mappeduser")).getId());
        transactions.execute(connection -> {
            var value = Optional.ofNullable(users.lock(id)).orElseThrow();
            assertEquals("previous-one", value.getPreviousPasswordHash1());
            assertEquals("previous-two", value.getPreviousPasswordHash2());
            assertNotNull(value.getEmailVerifiedAt());
            value.setDisplayName("Tên mới");
            value.getScopeMembershipVersions().put("123", 4L);
            users.saveAndFlush(value);
            assertEquals(1, value.getVersion());
            return null;
        });
        transactions.execute(connection -> {
            var value = users.findById(id).orElseThrow();
            assertEquals("Tên mới", value.getDisplayName());
            assertEquals(Map.of("123", 4L), value.getScopeMembershipVersions());
            return null;
        });
    }

    @Test
    void jdbcSeesJpaFlushAndClosingCallbackDoesNotCommitOrCloseTheSharedTransaction() {
        assertThrows(BusinessException.class, () ->
            transactions.execute(connection -> {
                var value = users.saveAndFlush(user("rollbackuser"));
                try (var query = connection.prepareStatement("UPDATE auth_users SET display_name='JDBC' WHERE id=?")) {
                    query.setLong(1, value.getId());
                    assertEquals(1, query.executeUpdate());
                }
                try (
                    var query = connection.prepareStatement(
                        "INSERT INTO auth_users(username,display_name,password_hash,created_at,updated_at,created_by,updated_by,scope_membership_versions) VALUES('rollbackuser','duplicate','hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0,'{}')"
                    )
                ) {
                    // Dùng lỗi SQL chắc chắn sau JPA flush, không phụ thuộc UNIQUE được tạo bởi Hibernate.
                    query.execute("SELECT * FROM table_that_does_not_exist");
                }
                return null;
            })
        );
        long count = transactions.execute(connection -> users.count());
        assertEquals(0L, count);
    }
}
