package vn.vietroute.common.config.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.constant.ApplicationConstants.BootstrapProperties;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Đọc cấu hình một lần; không có toString để tránh vô tình ghi secrets ra log. */
@Getter
public final class AppSettings {

    /** Chế độ production bật yêu cầu secret bền vững và cookie HTTPS mặc định. */
    private final boolean production;

    /** Bật migration/seed tại startup; không có tác dụng trong Maven packaging. */
    private final boolean databaseInitEnabled;

    /** Thu thập mapping sau khi servlet context sẵn sàng; mặc định đi cùng cờ migration startup. */
    private final boolean accessSyncEnabled;

    /** Cookie chỉ gửi qua HTTPS khi true; local HTTP dùng false. */
    private final boolean cookieSecure;

    /** Danh tính quản trị gốc dùng khi init/sync/khôi phục; không đồng nhất với mọi user có role ADMIN. */
    private final String bootstrapUsername;

    /** Mật khẩu seed từ môi trường; không reset hash cũ hoặc ghi ra log. */
    private final String bootstrapPassword;

    /** Email được chủ hệ thống cấp sẵn, chỉ dùng cho danh tính bootstrap cấu hình. */
    private final String bootstrapEmail;

    /** Khóa HMAC dùng ký/xác minh JWT, các node phải dùng cùng secret. */
    private final SecretKey jwtKey;

    /** Issuer cố định được decoder kiểm để loại token của hệ thống khác. */
    private final String jwtIssuer = "vietroute";

    /** Audience giới hạn token cho ứng dụng web này. */
    private final String jwtAudience = "vietroute-web";

    /** JWT hết hạn sau 30 phút; chưa có refresh token. */
    private final Duration accessTokenLifetime = Duration.ofMinutes(30);

    public AppSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        String environment = values.text(Env.ENV, "development").toLowerCase(Locale.ROOT);
        if (!environment.equals("development") && !environment.equals("production")) {
            throw new IllegalStateException(Env.ENV.key() + " must be development or production");
        }
        production = environment.equals("production");
        databaseInitEnabled = values.flag(Env.DB_INIT_ENABLED, false);
        accessSyncEnabled = values.flag(Env.ACCESS_SYNC_ENABLED, databaseInitEnabled);
        cookieSecure = values.flag(Env.COOKIE_SECURE, production);
        bootstrapUsername = values.text(Env.BOOTSTRAP_ADMIN_USERNAME, "admin");
        bootstrapPassword = values.text(Env.BOOTSTRAP_ADMIN_PASSWORD, "");
        bootstrapEmail = values.text(Env.BOOTSTRAP_ADMIN_EMAIL, bootstrapEmailDefault()).toLowerCase(Locale.ROOT);
        if (!MailSettings.validAddress(bootstrapEmail)) {
            throw new IllegalStateException(Env.BOOTSTRAP_ADMIN_EMAIL.key() + " is invalid");
        }
        if (!bootstrapUsername.matches(Regex.USERNAME) || bootstrapUsername.equalsIgnoreCase(SystemActor.USERNAME)) {
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

    /** Chỉ đọc email không bí mật từ classpath; không tự đọc .env hoặc credentials SMTP. */
    private static String bootstrapEmailDefault() {
        Properties defaults = new Properties();
        try (InputStream input = AppSettings.class.getResourceAsStream(BootstrapProperties.RESOURCE)) {
            if (input != null) defaults.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bootstrap configuration", exception);
        }
        return defaults.getProperty(BootstrapProperties.EMAIL, "");
    }
}
