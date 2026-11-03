package vn.vietroute.support;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.BiFunction;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
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
        factory.setPackagesToScan("vn.vietroute.auth.entity", "vn.vietroute.business");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        MANAGER = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
    }

    public static <T> T repository(Class<T> type, EntityManager manager) {
        return new JpaRepositoryFactory(manager).getRepository(type);
    }

    public static <T> T stub(Class<T> type, BiFunction<Method, Object[], Object> handler) {
        return type.cast(
            Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> type.getSimpleName() + " fixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new AssertionError(method);
                    };
                }
                return handler.apply(method, args);
            })
        );
    }

    private TestJpa() {}
}
