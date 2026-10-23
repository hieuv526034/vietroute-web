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

    @Bean
    public ActivitySettings activitySettings(ConfigurationEnvironment environment) {
        return new ActivitySettings(environment);
    }

    @Bean(destroyMethod = "close")
    public ApiJournalDatabase apiJournalDatabase(DatabaseSettings settings, JdbcSchema schema) {
        return new ApiJournalDatabase(settings, schema);
    }

    @Bean
    public ApiJournalSettings apiJournalSettings(ConfigurationEnvironment environment) {
        return new ApiJournalSettings(environment);
    }

    @Bean
    public MonitoringSettings monitoringSettings(ConfigurationEnvironment environment) {
        return new MonitoringSettings(environment);
    }

    @Bean(destroyMethod = "close")
    public PrometheusMeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
