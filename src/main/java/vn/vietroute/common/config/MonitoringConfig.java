package vn.vietroute.common.config;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registry riêng của ứng dụng, không dùng global static registry và không tự gửi dữ liệu ra ngoài. */
@Configuration
public class MonitoringConfig {

    @Bean
    public ApiJournalSettings apiJournalSettings() {
        return new ApiJournalSettings(System::getenv);
    }

    @Bean
    public MonitoringSettings monitoringSettings() {
        return new MonitoringSettings(System::getenv);
    }

    @Bean(destroyMethod = "close")
    public PrometheusMeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
