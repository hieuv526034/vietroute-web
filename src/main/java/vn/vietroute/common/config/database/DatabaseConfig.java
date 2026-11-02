package vn.vietroute.common.config.database;

import com.zaxxer.hikari.HikariDataSource;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class DatabaseConfig {

    private static final Logger LOG = Logger.getLogger(DatabaseConfig.class.getName());

    /** Kiểm cấu hình JDBC và ngưỡng pool trước khi bean DataSource được sử dụng. */
    @Bean
    public DatabaseSettings databaseSettings(AppSettings settings, ConfigurationEnvironment environment) {
        // Không in host/user/password; chỉ nêu nguồn giúp nhận ra cấu hình IDE cũ ghi đè .env.
        LOG.info(
            "event=database_configuration urlSource=" +
                environment.sourceOf(Env.DB_URL) +
                " userSource=" +
                environment.sourceOf(Env.DB_USER) +
                " passwordSource=" +
                environment.sourceOf(Env.DB_PASSWORD)
        );
        if (environment.sourceOf(Env.DB_URL).equals("DEFAULT")) {
            LOG.warning(
                "Chưa nhận VIETROUTE_DB_URL: đang dùng mẫu local theo profile database. Kiểm file .env và biến OS/IDE."
            );
        }
        return new DatabaseSettings(environment, settings.isProduction());
    }

    /**
     * Pool nhỏ cho Render Free và PostgreSQL: tạo lười, không giữ connection nhàn rỗi lâu hoặc ping giữ DB thức.
     * Spring đóng pool khi undeploy/shutdown; khởi tạo bean không tự kết nối DB khi initializer tắt.
     */
    @Bean(destroyMethod = "close")
    @Primary
    public HikariDataSource dataSource(DatabaseSettings settings) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("vietroute-jdbc");
        pool.setDriverClassName(settings.getDialect().driverClassName());
        pool.setJdbcUrl(settings.getUrl());
        pool.setUsername(settings.getUsername());
        pool.setPassword(settings.getPassword());
        pool.setMaximumPoolSize(settings.getMaximumPoolSize());
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(settings.getAcquisitionTimeoutMs());
        pool.setValidationTimeout(1000);
        pool.setIdleTimeout(60000);
        pool.setMaxLifetime(600000);
        pool.setKeepaliveTime(0);
        pool.setInitializationFailTimeout(-1);
        pool.setDataSourceProperties(
            settings
                .getDialect()
                .connectionProperties(
                    settings.getConnectTimeoutSeconds(),
                    settings.getSocketTimeoutSeconds(),
                    "vietroute-web",
                    true
                )
        );
        return pool;
    }

    /** Chặn hệ DB chưa có runtime trước initializer/repo, kể cả khi tất cả cờ init đã tắt. */
    @Bean
    public JdbcSchema jdbcSchema(DatabaseSettings settings) {
        return new JdbcSchema(settings.getSchema(), settings.getDialect());
    }

    /** Mỗi connection mượn từ pool được đặt lại search_path để tránh dùng nhầm schema. */
    @Bean
    @Primary
    public JdbcConnectionFactory jdbcConnectionFactory(
        @Qualifier("applicationDataSource") DataSource source,
        JdbcSchema schema
    ) {
        return new JdbcConnectionFactory(source, schema);
    }

    /** Nhật ký dùng pool riêng tối đa một connection, không giữ slot của login/nghiệp vụ. */
    @Bean(name = "journalDataSource", destroyMethod = "close")
    public HikariDataSource journalDataSource(DatabaseSettings settings) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("vietroute-api-journal");
        pool.setDriverClassName(settings.getDialect().driverClassName());
        pool.setJdbcUrl(settings.getUrl());
        pool.setUsername(settings.getUsername());
        pool.setPassword(settings.getPassword());
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(250);
        pool.setValidationTimeout(1000);
        pool.setIdleTimeout(60000);
        pool.setMaxLifetime(600000);
        pool.setInitializationFailTimeout(-1);
        pool.setDataSourceProperties(settings.getDialect().connectionProperties(2, 2, "vietroute-api-journal", false));
        return pool;
    }

    @Bean("journalConnections")
    public JdbcConnectionFactory journalConnections(
        @Qualifier("journalDataSource") DataSource source,
        JdbcSchema schema
    ) {
        return new JdbcConnectionFactory(source, schema);
    }
}
