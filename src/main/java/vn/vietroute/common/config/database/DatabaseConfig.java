package vn.vietroute.common.config.database;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class DatabaseConfig {

    /** Ngưỡng lịch sử quyền được kiểm ngay startup, không chứa secret hoặc trạng thái người dùng. */
    @Bean
    public AccessSettings accessSettings(ConfigurationEnvironment environment) {
        return new AccessSettings(environment);
    }

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
                "Chưa nhận VIETROUTE_DB_URL: đang dùng mẫu local trong database.properties. Chọn VietRoute Local, kiểm working directory/file .env và biến OS/IDE."
            );
        }
        return new DatabaseSettings(environment, settings.isProduction());
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

    /** Đóng gói tên schema đã validate; không dùng schema từ dữ liệu HTTP. */
    @Bean
    public JdbcSchema jdbcSchema(DatabaseSettings settings) {
        return new JdbcSchema(settings.getSchema(), settings.getDialect());
    }

    /** Mỗi connection mượn từ pool được đặt lại search_path để tránh dùng nhầm schema. */
    @Bean
    public JdbcConnectionFactory jdbcConnectionFactory(DataSource source, JdbcSchema schema) {
        return new JdbcConnectionFactory(source, schema);
    }
}
