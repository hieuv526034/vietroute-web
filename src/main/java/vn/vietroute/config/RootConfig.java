package vn.vietroute.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

@Configuration
@ComponentScan(basePackages = {
        "vn.vietroute.identity", "vn.vietroute.network",
        "vn.vietroute.booking", "vn.vietroute.tracking"
})
public class RootConfig {
    @Bean
    public JdbcConnectionFactory jdbcConnectionFactory() {
        return JdbcConnectionFactory.fromEnvironment();
    }
}
