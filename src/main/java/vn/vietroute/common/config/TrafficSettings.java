package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.constant.ApplicationConstants.Env;

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
        requestsPerMinute = values.number(Env.RATE_REQUESTS, 300, 1, 100000);
        loginsPerMinute = values.number(Env.RATE_LOGINS, 20, 1, 1000);
        maxConcurrent = values.number(Env.HTTP_IN_FLIGHT, 16, 1, 512);
        maxConcurrentLogins = values.number(Env.LOGIN_IN_FLIGHT, 2, 1, 16);
        maxBodyBytes = values.number(Env.HTTP_MAX_BODY_BYTES, 65536, 1024, 1048576);
        maxKeys = values.number(Env.RATE_MAX_KEYS, 10000, 100, 100000);
        redisUrl = values.text(Env.REDIS_URL, "");
        proxyCidrs = values.text(Env.TRUSTED_PROXY_CIDRS, "");
        if (!redisUrl.isBlank() && !redisUrl.startsWith("redis://") && !redisUrl.startsWith("rediss://")) {
            throw new IllegalStateException(Env.REDIS_URL.key() + " must use redis/rediss");
        }
    }
}
