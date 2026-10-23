package vn.vietroute.common.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import vn.vietroute.auth.config.SecurityConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.database.DatabaseConfig;
import vn.vietroute.common.config.integration.IntegrationConfig;
import vn.vietroute.common.config.monitoring.MonitoringConfig;
import vn.vietroute.common.config.traffic.TrafficConfig;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

/** Root context quản lý service/repository và hạ tầng; không quét controller để tránh tạo bean hai lần. */
@Configuration
@ComponentScan(
    basePackages = { "vn.vietroute.common", "vn.vietroute.auth", "vn.vietroute.business" },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = { Controller.class, ControllerAdvice.class, Configuration.class }
    )
)
@Import({
    SecurityConfig.class,
    DatabaseConfig.class,
    IntegrationConfig.class,
    TrafficConfig.class,
    MonitoringConfig.class,
})
public class RootConfig {

    /** Cùng một Clock UTC cho audit, JWT và nghiệp vụ để dễ kiểm thử thời gian. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Một nguồn cấu hình dùng chung giúp mọi Settings tuân cùng thứ tự ưu tiên OS/IDE rồi file local. */
    @Bean
    public ConfigurationEnvironment configurationEnvironment() {
        return new ConfigurationEnvironment();
    }

    /** Đọc và validate cấu hình nền một lần, không in đối tượng chứa secret. */
    @Bean
    public AppSettings appSettings(ConfigurationEnvironment environment) {
        return new AppSettings(environment);
    }

    @Bean
    @Primary
    public SupportedLocaleResolver supportedLocaleResolver() {
        return new SupportedLocaleResolver();
    }

    /** Nạp hai bundle UTF-8; không phụ thuộc locale của máy chạy hoặc tự dịch thông báo. */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(SupportedLocaleResolver.DEFAULT);
        return source;
    }

    /** JSON không cho ghi trường lạ/audit ngoài request DTO; Instant luôn trả ISO-8601. */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    }
}
