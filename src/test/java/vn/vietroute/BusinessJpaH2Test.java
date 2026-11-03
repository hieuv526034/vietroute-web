package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.business.catalog.repo.CategoryRepo;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** H2 kiểm CRUD JPA và rollback JDBC chung; dialect/index/scope thực vẫn được kiểm bằng PostgreSQL cô lập. */
class BusinessJpaH2Test {

    private LocalContainerEntityManagerFactoryBean factory;
    private CategoryRepo catalogs;
    private JdbcTransactions transactions;

    @BeforeEach
    void start() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:business_jpa_" +
                UUID.randomUUID() +
                ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        source.setUser("sa");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("vn.vietroute.business.catalog.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaTransactionManager manager = new JpaTransactionManager(emf);
        manager.setDataSource(source);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("transactionManager", manager);
        transactions = new JdbcTransactions(
            new JdbcConnectionFactory(new TransactionAwareDataSourceProxy(source), new JdbcSchema("h2_business")),
            beans.getBeanProvider(PlatformTransactionManager.class)
        );
        catalogs = new CategoryRepo(shared, transactions, new ObjectMapper());
    }

    @AfterEach
    void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void crudPreservesAuditAndVersionAndRejectsStaleUpdate() {
        long id = transactions.execute(connection -> catalogs.saveAndFlush(create("H2CAT")).getId());
        Category changed = transactions.execute(connection -> update(id, 0));
        assertEquals(1, changed.getVersion());
        assertNotNull(changed.getCreatedAt());
        assertNotNull(changed.getUpdatedAt());
        assertEquals(0L, changed.getCreatedBy());
        assertEquals(0L, changed.getUpdatedBy());
        assertFalse(changed.isActive());
        assertThrows(BusinessException.class, () -> transactions.execute(connection -> update(id, 0)));
        transactions.execute(connection -> {
            Category actual = catalogs.findById(id).orElseThrow();
            assertEquals("Tên mới", actual.getName());
            assertEquals("H2CAT", actual.getCode());
            assertEquals(16, actual.getProps().get("seatCount"));
            assertEquals(1, actual.getVersion());
            return null;
        });
    }

    @Test
    void jdbcObservesFlushedJpaAndFailureRollsBackInsertAndUpdate() {
        assertThrows(BusinessException.class, () ->
            transactions.execute(connection -> {
                catalogs.saveAndFlush(create("ROLLBACK"));
                try (PreparedStatement query = connection.prepareStatement("SELECT * FROM missing_business_table")) {
                    query.execute();
                }
                return null;
            })
        );
        long count = transactions.execute(connection -> catalogs.count());
        assertEquals(0L, count);
        long id = transactions.execute(connection -> catalogs.saveAndFlush(create("H2CAT")).getId());
        assertThrows(BusinessException.class, () ->
            transactions.execute(connection -> {
                update(id, 0);
                try (PreparedStatement query = connection.prepareStatement("SELECT name FROM category WHERE id=?")) {
                    query.setLong(1, id);
                    try (ResultSet row = query.executeQuery()) {
                        row.next();
                        assertEquals("Tên mới", row.getString(1));
                    }
                }
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            })
        );
        transactions.execute(connection -> {
            Category actual = catalogs.findById(id).orElseThrow();
            assertEquals("Ghế ngồi", actual.getName());
            assertEquals(0, actual.getVersion());
            return null;
        });
    }

    private Category create(String code) {
        return Category.builder()
            .code(code)
            .type("VALUE")
            .refType("VEHICLE_TYPE")
            .name("Ghế ngồi")
            .props(Map.of("seatCount", 16))
            .active(true)
            .createdAt(Instant.EPOCH)
            .updatedAt(Instant.EPOCH)
            .createdBy(0L)
            .updatedBy(0L)
            .build();
    }

    private Category update(long id, long version) {
        Category value = catalogs.locked(id);
        BusinessRules.requireVersion(version, value.getVersion());
        value.setName("Tên mới");
        value.setActive(false);
        value.setUpdatedAt(Instant.now());
        return catalogs.saveAndFlush(value);
    }
}
