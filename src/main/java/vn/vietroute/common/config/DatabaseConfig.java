package vn.vietroute.common.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class DatabaseConfig {

    @Bean
    public DatabaseSettings databaseSettings(AppSettings settings) {
        return new DatabaseSettings(System::getenv, settings.isProduction());
    }

    /**
     * Pool nhỏ cho Render Free/Neon: tạo lười, không giữ connection nhàn rỗi lâu hoặc ping giữ DB thức.
     * Spring đóng pool khi undeploy/shutdown; khởi tạo bean không tự kết nối DB khi initializer tắt.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(DatabaseSettings settings, PrometheusMeterRegistry registry) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("vietroute-jdbc");
        pool.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
        pool.setDriverClassName("org.postgresql.Driver");
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
        pool.addDataSourceProperty("connectTimeout", settings.getConnectTimeoutSeconds());
        pool.addDataSourceProperty("socketTimeout", settings.getSocketTimeoutSeconds());
        pool.addDataSourceProperty("tcpKeepAlive", true);
        pool.addDataSourceProperty("ApplicationName", "vietroute-web");
        return pool;
    }

    @Bean
    public JdbcSchema jdbcSchema(DatabaseSettings settings) {
        return new JdbcSchema(settings.getSchema());
    }

    @Bean
    public JdbcConnectionFactory jdbcConnectionFactory(DataSource source, JdbcSchema schema) {
        return new JdbcConnectionFactory(source, schema);
    }
}
