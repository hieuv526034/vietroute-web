package vn.vietroute.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import vn.vietroute.common.web.SupportedLocaleResolver;
import vn.vietroute.auth.config.SecurityConfig;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

@Configuration
@ComponentScan(basePackages = {
        "vn.vietroute.common", "vn.vietroute.auth", "vn.vietroute.business"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
        classes = {Controller.class, ControllerAdvice.class, Configuration.class}))
@Import(SecurityConfig.class)
public class RootConfig {
    @Bean
    public JdbcConnectionFactory jdbcConnectionFactory() {
        return JdbcConnectionFactory.fromEnvironment();
    }
    /** Cùng một Clock UTC cho audit, JWT và nghiệp vụ để dễ kiểm thử thời gian. */
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public AppSettings appSettings() { return new AppSettings(System::getenv); }
    @Bean @org.springframework.context.annotation.Primary
    public SupportedLocaleResolver supportedLocaleResolver() { return new SupportedLocaleResolver(); }
    @Bean public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages"); source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false); source.setDefaultLocale(SupportedLocaleResolver.DEFAULT);
        return source;
    }
    /** JSON không cho ghi trường lạ/audit ngoài request DTO; Instant luôn trả ISO-8601. */
    @Bean public ObjectMapper objectMapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }
}
