package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;

/** Giới hạn nhỏ cho Render Free; khi scale nhiều instance chọn Redis để chia sẻ quota. */
@Getter
public final class TrafficSettings {

    private final int requestsPerMinute;

    private final int loginsPerMinute;

    private final int maxConcurrent;

    private final int maxConcurrentLogins;

    private final int maxBodyBytes;

    private final int maxKeys;

    private final String redisUrl;

    private final String proxyCidrs;

    public TrafficSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        requestsPerMinute = values.number("VIETROUTE_RATE_REQUESTS", 300, 1, 100000);
        loginsPerMinute = values.number("VIETROUTE_RATE_LOGINS", 20, 1, 1000);
        maxConcurrent = values.number("VIETROUTE_HTTP_IN_FLIGHT", 16, 1, 512);
        maxConcurrentLogins = values.number("VIETROUTE_LOGIN_IN_FLIGHT", 2, 1, 16);
        maxBodyBytes = values.number("VIETROUTE_HTTP_MAX_BODY_BYTES", 65536, 1024, 1048576);
        maxKeys = values.number("VIETROUTE_RATE_MAX_KEYS", 10000, 100, 100000);
        redisUrl = values.text("VIETROUTE_REDIS_URL", "");
        proxyCidrs = values.text("VIETROUTE_TRUSTED_PROXY_CIDRS", "");
        if (!redisUrl.isBlank() && !redisUrl.startsWith("redis://") && !redisUrl.startsWith("rediss://")) {
            throw new IllegalStateException("VIETROUTE_REDIS_URL must use redis/rediss");
        }
    }
}
