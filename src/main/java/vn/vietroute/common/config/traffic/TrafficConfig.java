package vn.vietroute.common.config.traffic;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.traffic.MemoryRateLimitStore;
import vn.vietroute.common.traffic.RateLimitStore;
import vn.vietroute.common.traffic.RedisRateLimitStore;
import vn.vietroute.common.traffic.TrafficGuardFilter;
import vn.vietroute.common.web.response.ApiErrorWriter;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class TrafficConfig {

    /** Validate quota/concurrency/body/proxy trước khi đưa request vào luồng nghiệp vụ. */
    @Bean
    public TrafficSettings trafficSettings(ConfigurationEnvironment environment) {
        return new TrafficSettings(environment);
    }

    /** Mặc định quota memory mỗi node; adapter Redis chỉ chọn khi có URL tường minh. */
    @Bean(destroyMethod = "close")
    public RateLimitStore rateLimitStore(TrafficSettings settings, Clock clock) {
        return settings.getRedisUrl().isBlank()
            ? new MemoryRateLimitStore(clock, settings.getMaxKeys())
            : new RedisRateLimitStore(settings.getRedisUrl());
    }

    /** Ghép quota, giới hạn request đồng thời và lỗi HTTP chung, không bỏ qua service guard. */
    @Bean
    public TrafficGuardFilter trafficGuardFilter(
        TrafficSettings settings,
        RateLimitStore store,
        ApiErrorWriter errors
    ) {
        return new TrafficGuardFilter(settings, store, errors);
    }
}
