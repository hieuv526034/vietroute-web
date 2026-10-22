package vn.vietroute.common.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import javax.sql.DataSource;
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
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.web.SupportedLocaleResolver;

/** Root context quản lý service/repository và hạ tầng; không quét controller để tránh tạo bean hai lần. */
@Configuration
@ComponentScan(
    basePackages = { "vn.vietroute.common", "vn.vietroute.auth", "vn.vietroute.business" },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = { Controller.class, ControllerAdvice.class, Configuration.class }
    )
)
@Import(SecurityConfig.class)
public class RootConfig {

    @Bean
    public DatabaseSettings databaseSettings(AppSettings settings) {
        return new DatabaseSettings(System::getenv, settings.isProduction());
    }

    /**
     * Pool nhỏ cho Render Free/Neon: tạo lười, không giữ connection nhàn rỗi lâu hoặc ping giữ DB thức.
     * Spring đóng pool khi undeploy/shutdown; khởi tạo bean không tự kết nối DB khi initializer tắt.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(DatabaseSettings settings) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("vietroute-jdbc");
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
    public JdbcConnectionFactory jdbcConnectionFactory(DataSource source) {
        return new JdbcConnectionFactory(source);
    }

    /** Cùng một Clock UTC cho audit, JWT và nghiệp vụ để dễ kiểm thử thời gian. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AppSettings appSettings() {
        return new AppSettings(System::getenv);
    }

    @Bean
    @Primary
    public SupportedLocaleResolver supportedLocaleResolver() {
        return new SupportedLocaleResolver();
    }

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
