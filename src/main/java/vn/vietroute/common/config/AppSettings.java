package vn.vietroute.common.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Function;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
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
        ConfigValues values = new ConfigValues(source);
        String environment = values.text(Env.ENV, "development").toLowerCase(Locale.ROOT);
        if (!environment.equals("development") && !environment.equals("production")) {
            throw new IllegalStateException(Env.ENV.key() + " must be development or production");
        }
        production = environment.equals("production");
        databaseInitEnabled = values.flag(Env.DB_INIT_ENABLED, false);
        cookieSecure = values.flag(Env.COOKIE_SECURE, production);
        bootstrapUsername = values.text(Env.BOOTSTRAP_ADMIN_USERNAME, "admin");
        bootstrapPassword = values.text(Env.BOOTSTRAP_ADMIN_PASSWORD, "");
        if (!bootstrapUsername.matches(Regex.USERNAME)) {
            throw new IllegalStateException(Env.BOOTSTRAP_ADMIN_USERNAME.key() + " is invalid");
        }
        if (databaseInitEnabled && !StrongPasswordValidator.valid(bootstrapPassword)) {
            throw new IllegalStateException(Env.BOOTSTRAP_ADMIN_PASSWORD.key() + " must satisfy the password policy");
        }
        String secret = values.raw(Env.JWT_SECRET);
        if (secret == null || secret.isBlank()) {
            if (production) {
                throw new IllegalStateException(Env.JWT_SECRET.key() + " is required in production");
            }
            // Chỉ development không cấu hình mới dùng khóa tạm, token mất hiệu lực sau restart.
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(Env.JWT_SECRET.key() + " must contain at least 32 UTF-8 bytes");
        }
        if (production && secret.startsWith("replace_with_")) {
            throw new IllegalStateException(
                Env.JWT_SECRET.key() + " must not use the example placeholder in production"
            );
        }
        jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
