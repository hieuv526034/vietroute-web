package vn.vietroute.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Cấu hình JDBC/pool có giới hạn; không tạo toString để tránh ghi credentials ra log. */
@Getter
public final class DatabaseSettings {

    private final String url;

    private final String username;

    private final String password;

    private final String schema;

    private final int maximumPoolSize;

    private final int acquisitionTimeoutMs;

    private final int connectTimeoutSeconds;

    private final int socketTimeoutSeconds;

    public DatabaseSettings(Function<String, String> source, boolean production) {
        Properties defaults = defaults();
        url = setting(source, "VIETROUTE_DB_URL", defaults.getProperty("db.url", ""));
        username = setting(source, "VIETROUTE_DB_USER", defaults.getProperty("db.username", ""));
        password = setting(source, "VIETROUTE_DB_PASSWORD", "");
        schema = setting(source, "VIETROUTE_DB_SCHEMA", defaults.getProperty("db.schema", ""));
        // Schema là identifier do người vận hành cấu hình, không lấy từ HTTP hoặc ghép tùy ý vào SQL.
        JdbcSchema.validate(schema);
        if (!url.startsWith("jdbc:postgresql://") || username.isBlank()) {
            throw new IllegalStateException("VIETROUTE_DB_URL/VIETROUTE_DB_USER are invalid");
        }
        // Production không được âm thầm dùng localhost/default khi thiếu Environment trên Render.
        if (
            production &&
            (source.apply("VIETROUTE_DB_URL") == null ||
                source.apply("VIETROUTE_DB_USER") == null ||
                password.isBlank())
        ) {
            throw new IllegalStateException("Production requires explicit VIETROUTE_DB_URL/USER/PASSWORD");
        }
        maximumPoolSize = number(source, "VIETROUTE_DB_POOL_SIZE", 4, 1, 10);
        acquisitionTimeoutMs = number(source, "VIETROUTE_DB_ACQUIRE_TIMEOUT_MS", 20000, 1000, 60000);
        connectTimeoutSeconds = number(source, "VIETROUTE_DB_CONNECT_TIMEOUT_SECONDS", 15, 1, 60);
        socketTimeoutSeconds = number(source, "VIETROUTE_DB_SOCKET_TIMEOUT_SECONDS", 30, 1, 300);
    }

    /** Chỉ nạp file mẫu không chứa secret trong classpath, Java không tự đọc .env. */
    private static Properties defaults() {
        Properties values = new Properties();
        try (InputStream input = DatabaseSettings.class.getResourceAsStream("/database.properties")) {
            if (input != null) {
                values.load(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read database configuration", exception);
        }
        return values;
    }

    private static String setting(Function<String, String> source, String key, String fallback) {
        String value = source.apply(key);
        return value == null ? fallback : value;
    }

    /** Từ chối giá trị lỗi/ngoài khoảng thay vì chạy pool quá lớn hoặc chờ vô hạn. */
    private static int number(Function<String, String> source, String key, int fallback, int min, int max) {
        String value = source.apply(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Không đính kèm giá trị môi trường vào exception/log.
        }
        throw new IllegalStateException(key + " must be between " + min + " and " + max);
    }
}
