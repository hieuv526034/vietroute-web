package vn.vietroute.common.config.database;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Spring MVC thuần: JPA CRUD và JDBC batch/SQL dùng cùng connection và ranh giới commit/rollback. */
@Configuration
@EnableJpaRepositories(basePackages = { "vn.vietroute.auth.repo", "vn.vietroute.business.trip.repo" })
@EnableTransactionManagement(proxyTargetClass = true)
public class JpaConfig {

    /** Chuẩn hóa lỗi Hibernate ở @Repository thành lỗi Spring như JDBC: unique/FK409, version409. */
    @Bean
    public static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    @Bean
    public SchemaDataSource jpaDataSource(@Qualifier("dataSource") DataSource pool, JdbcSchema schema) {
        return new SchemaDataSource(pool, schema);
    }

    /** Proxy cho các DAO JDBC hiện có: close trong callback không đóng connection của transaction JPA. */
    @Bean
    @Primary
    public DataSource applicationDataSource(SchemaDataSource jpaDataSource) {
        return new TransactionAwareDataSourceProxy(jpaDataSource);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        SchemaDataSource jpaDataSource,
        JdbcSchema schema,
        DatabaseSettings settings
    ) {
        HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
        vendor.setGenerateDdl(false);
        vendor.setShowSql(false);
        HibernateJpaDialect dialect = new HibernateJpaDialect();
        // Connection đã mở transaction để chọn LOCAL search_path; không đổi readOnly/isolation giữa transaction.
        dialect.setPrepareConnection(false);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendor);
        factory.setJpaDialect(dialect);
        factory.setDataSource(jpaDataSource);
        factory.setPackagesToScan(
            "vn.vietroute.auth.entity",
            "vn.vietroute.business",
            "vn.vietroute.common.api_journal.entity"
        );
        Properties properties = new Properties();
        settings.getDialect().requireApplicationSupport();
        properties.setProperty("hibernate.dialect", settings.getDialect().hibernateDialectClassName());
        properties.setProperty("hibernate.default_schema", schema.name());
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("jakarta.persistence.schema-generation.database.action", "none");
        properties.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        properties.setProperty(
            "hibernate.connection.handling_mode",
            "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION"
        );
        properties.setProperty("hibernate.jdbc.batch_size", "100");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");
        factory.setJpaProperties(properties);
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(
        EntityManagerFactory entityManagerFactory,
        SchemaDataSource jpaDataSource
    ) {
        JpaTransactionManager manager = new JpaTransactionManager(entityManagerFactory);
        manager.setDataSource(jpaDataSource);
        HibernateJpaDialect dialect = new HibernateJpaDialect();
        dialect.setPrepareConnection(false);
        manager.setJpaDialect(dialect);
        manager.setNestedTransactionAllowed(false);
        return manager;
    }

    @Bean
    @Primary
    public EntityManager entityManager(EntityManagerFactory factory) {
        return SharedEntityManagerCreator.createSharedEntityManager(factory);
    }
}
