package vn.vietroute.common.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Function;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import vn.vietroute.common.constant.Constants;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Đọc cấu hình một lần; không có toString để tránh vô tình ghi secrets ra log. */
@Getter
public final class AppSettings {
    private final boolean production;
    private final boolean databaseInitEnabled;
    private final boolean cookieSecure;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final SecretKey jwtKey;
    private final String jwtIssuer = "vietroute";
    private final String jwtAudience = "vietroute-web";
    private final Duration accessTokenLifetime = Duration.ofMinutes(30);

    public AppSettings(Function<String, String> source) {
        production = "production".equalsIgnoreCase(value(source, "VIETROUTE_ENV", "development"));
        databaseInitEnabled = flag(source, "VIETROUTE_DB_INIT_ENABLED", false);
        cookieSecure = flag(source, "VIETROUTE_COOKIE_SECURE", production);
        bootstrapUsername = value(source, "VIETROUTE_BOOTSTRAP_ADMIN_USERNAME", "admin");
        bootstrapPassword = value(source, "VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD", "");
        if (!bootstrapUsername.matches(Constants.USERNAME_PATTERN)) {
            throw new IllegalStateException("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME is invalid");
        }
        if (databaseInitEnabled && !StrongPasswordValidator.valid(bootstrapPassword)) {
            throw new IllegalStateException("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD must satisfy the password policy");
        }
        String secret = source.apply("VIETROUTE_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            if (production) { throw new IllegalStateException("VIETROUTE_JWT_SECRET is required in production"); }
            // Chỉ development không cấu hình mới dùng khóa tạm, token mất hiệu lực sau restart.
            byte[] random = new byte[48]; new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("VIETROUTE_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    private static String value(Function<String, String> source, String key, String fallback) {
        String value = source.apply(key); return value == null ? fallback : value;
    }
    private static boolean flag(Function<String, String> source, String key, boolean fallback) {
        String value = source.apply(key);
        if (value == null) { return fallback; }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
