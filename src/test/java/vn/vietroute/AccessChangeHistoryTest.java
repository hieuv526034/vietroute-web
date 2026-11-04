package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.support.TestJpa;

/** H2 kiểm history JPA thật, JDBC thấy cùng transaction và rollback xóa toàn bộ history chưa commit. */
class AccessChangeHistoryTest {

    @Test
    void explicitMappingStatesDoNotSerializeAccountCredentialsAndRollbackRestoresHistory() throws Exception {
        var source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:changes_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        source.setUser("sa");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("vn.vietroute.auth.entity");
        factory.setJpaPropertyMap(
            Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.default_schema", "public")
        );
        factory.afterPropertiesSet();
        try (var manager = factory.getObject().createEntityManager()) {
            manager.getTransaction().begin();
            var connection = manager.unwrap(Session.class).doReturningWork(value -> value);
            TestJpa.repository(AccessChangeRepo.class, manager).append(
                "USER_ROLES",
                "USER",
                "7",
                2,
                Map.of("roles", Set.of("OLD")),
                Map.of("roles", Set.of("NEW"))
            );
            try (
                var query = connection.createStatement();
                var row = query.executeQuery("SELECT before_state,after_state,actor_id FROM auth_access_changes")
            ) {
                assertTrue(row.next());
                assertEquals(2, row.getLong(3));
                assertTrue(row.getString(1).contains("OLD"));
                assertTrue(row.getString(2).contains("NEW"));
            }
            manager.getTransaction().rollback();
            manager.clear();
            manager.getTransaction().begin();
            connection = manager.unwrap(Session.class).doReturningWork(value -> value);
            try (
                var query = connection.createStatement();
                var row = query.executeQuery("SELECT COUNT(*) FROM auth_access_changes")
            ) {
                assertTrue(row.next());
                assertEquals(0, row.getLong(1));
            }
            manager.getTransaction().rollback();
        } finally {
            factory.destroy();
        }
    }
}
