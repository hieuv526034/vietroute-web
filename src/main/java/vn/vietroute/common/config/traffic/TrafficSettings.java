package vn.vietroute.common.config.traffic;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Giới hạn nhỏ cho Render Free; khi scale nhiều instance chọn Redis để chia sẻ quota. */
@Getter
public final class TrafficSettings {

    /** Quota request mỗi phút theo chủ thể và node khi dùng memory store; mặc định 300. */
    private final int requestsPerMinute;

    /** Quota thử đăng nhập mỗi phút, mặc định 20; khác khóa tài khoản do sai mật khẩu. */
    private final int loginsPerMinute;

    /** Trần request đang xử lý trên mỗi node, mặc định 16, vượt trần trả 503. */
    private final int maxConcurrent;

    /** Trần xử lý đăng nhập/BCrypt đồng thời, mặc định 2 để giữ CPU/RAM. */
    private final int maxConcurrentLogins;

    /** Trần byte JSON/request thông thường, mặc định 65536; upload ảnh có giới hạn riêng. */
    private final int maxBodyBytes;

    /** Trần số chủ thể giữ trong quota memory, mặc định 10000 để hạn chế RAM. */
    private final int maxKeys;

    /** Adapter cũ tùy chọn; rỗng dùng memory theo node, hiện chưa triển khai Redis. */
    private final String redisUrl;

    /** CIDR proxy được tin cậy khi đọc IP chuyển tiếp; rỗng dùng địa chỉ kết nối trực tiếp. */
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
