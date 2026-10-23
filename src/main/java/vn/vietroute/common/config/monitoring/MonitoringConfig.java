package vn.vietroute.common.config.monitoring;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.monitoring.repository.ApiJournalDatabase;

/** Registry riêng của ứng dụng, không dùng global static registry và không tự gửi dữ liệu ra ngoài. */
@Configuration
public class MonitoringConfig {

    /** Cờ thống kê truy cập và ngưỡng cảnh báo, không tự bật chặn người dùng. */
    @Bean
    public ActivitySettings activitySettings(ConfigurationEnvironment environment) {
        return new ActivitySettings(environment);
    }

    /** Pool quan sát riêng tối đa một connection/node; đóng pool khi ứng dụng dừng. */
    @Bean(destroyMethod = "close")
    public ApiJournalDatabase apiJournalDatabase(DatabaseSettings settings, JdbcSchema schema) {
        return new ApiJournalDatabase(settings, schema);
    }

    /** Cấu hình retention/capacity của lịch sử DB, không tạo cache lịch sử trong RAM. */
    @Bean
    public ApiJournalSettings apiJournalSettings(ConfigurationEnvironment environment) {
        return new ApiJournalSettings(environment);
    }

    /** Tách token scrape và probe DB khỏi JWT người dùng. */
    @Bean
    public MonitoringSettings monitoringSettings(ConfigurationEnvironment environment) {
        return new MonitoringSettings(environment);
    }

    /** Registry metrics thuộc vòng đời ứng dụng, không tự gửi số liệu hay bật cảnh báo bên ngoài. */
    @Bean(destroyMethod = "close")
    public PrometheusMeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
