package vn.vietroute.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.traffic.MemoryRateLimitStore;
import vn.vietroute.common.traffic.RateLimitStore;
import vn.vietroute.common.traffic.RedisRateLimitStore;
import vn.vietroute.common.traffic.TrafficGuardFilter;
import vn.vietroute.common.web.response.ApiErrorWriter;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class TrafficConfig {

    @Bean
    public TrafficSettings trafficSettings() {
        return new TrafficSettings(System::getenv);
    }

    @Bean(destroyMethod = "close")
    public RateLimitStore rateLimitStore(TrafficSettings settings, Clock clock) {
        return settings.getRedisUrl().isBlank()
            ? new MemoryRateLimitStore(clock, settings.getMaxKeys())
            : new RedisRateLimitStore(settings.getRedisUrl());
    }

    @Bean
    public TrafficGuardFilter trafficGuardFilter(
        TrafficSettings settings,
        RateLimitStore store,
        ApiErrorWriter errors
    ) {
        return new TrafficGuardFilter(settings, store, errors);
    }
}
