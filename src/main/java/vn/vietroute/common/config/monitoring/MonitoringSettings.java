package vn.vietroute.common.config.monitoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Http;

/** Metrics dùng token riêng, không phụ thuộc DB/JWT; không cung cấp getter hoặc log token. */
public final class MonitoringSettings {

    /** Chỉ giữ SHA-256 token scrape để so sánh constant-time; không cung cấp getter. */
    private final byte[] tokenDigest;

    @Getter
    /** Token hợp lệ không rỗng mới mở metrics; cookie/JWT không thay thế token này. */
    private final boolean scrapeEnabled;

    @Getter
    /** Probe DB chủ động, mặc định false để không ping giữ Neon thức. */
    private final boolean databaseEnabled;

    @Getter
    /** Chu kỳ UI đọc trạng thái, đơn vị giây, mặc định 30, tối thiểu 15. */
    private final int pollSeconds;

    public MonitoringSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        String token = values.text(Env.METRICS_TOKEN, "");
        scrapeEnabled = !token.isBlank();
        if (
            scrapeEnabled &&
            (token.length() < 32 || token.length() > 256 || token.chars().anyMatch(c -> c < 33 || c > 126))
        ) {
            throw new IllegalStateException(Env.METRICS_TOKEN.key() + " requires 32-256 printable ASCII characters");
        }
        tokenDigest = digest(token);
        databaseEnabled = values.flag(Env.MONITORING_DB_ENABLED, false);
        pollSeconds = values.number(Env.MONITORING_POLL_SECONDS, 30, 15, 300);
    }

    /** Hash cố định độ dài rồi so sánh constant-time; cookie/JWT tài khoản không thay token vận hành. */
    public boolean accepts(String authorization) {
        if (
            !scrapeEnabled ||
            authorization == null ||
            !authorization.startsWith(Http.BEARER_PREFIX) ||
            authorization.length() > 300
        ) {
            return false;
        }
        return MessageDigest.isEqual(tokenDigest, digest(authorization.substring(Http.BEARER_PREFIX.length())));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
