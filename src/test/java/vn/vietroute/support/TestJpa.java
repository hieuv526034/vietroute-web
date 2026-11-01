package vn.vietroute.support;

import jakarta.persistence.EntityManager;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** Metamodel thật cho Repo test double; H2 riêng, không đọc env hoặc kết nối PostgreSQL/Neon. */
public final class TestJpa {

    public static final EntityManager MANAGER;

    static {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:repo_metadata;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("vn.vietroute.auth.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        MANAGER = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
    }

    private TestJpa() {}
}
