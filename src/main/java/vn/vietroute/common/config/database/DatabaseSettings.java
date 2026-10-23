package vn.vietroute.common.config.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.DatabaseProperties;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Cấu hình JDBC/pool có giới hạn; không tạo toString để tránh ghi credentials ra log. */
@Getter
public final class DatabaseSettings {

    /** JDBC PostgreSQL URL; nhận từ môi trường, production không được dùng localhost dự phòng. */
    private final String url;

    /** Tài khoản kết nối DB; không phải tài khoản đăng nhập ứng dụng. */
    private final String username;

    /** Mật khẩu DB chỉ giữ trong settings riêng, không serialize/log. */
    private final String password;

    /** Schema được validate trước khi đặt search_path trên từng connection. */
    private final String schema;

    /** Trần connection nghiệp vụ trên mỗi node, mặc định 4 để phù hợp Neon Free. */
    private final int maximumPoolSize;

    /** Mili giây chờ mượn connection từ pool, mặc định 20000; hết hạn trả lỗi dữ liệu. */
    private final int acquisitionTimeoutMs;

    /** Giây tối đa tạo kết nối TCP/SSL tới PostgreSQL, mặc định 15. */
    private final int connectTimeoutSeconds;

    /** Giây chờ đọc dữ liệu từ PostgreSQL, mặc định 30; không thay statement timeout. */
    private final int socketTimeoutSeconds;

    public DatabaseSettings(Function<String, String> source, boolean production) {
        Properties defaults = defaults();
        ConfigValues values = new ConfigValues(source);
        url = values.text(Env.DB_URL, defaults.getProperty(DatabaseProperties.URL, ""));
        username = values.text(Env.DB_USER, defaults.getProperty(DatabaseProperties.USERNAME, ""));
        password = values.text(Env.DB_PASSWORD, "");
        schema = values.text(Env.DB_SCHEMA, defaults.getProperty(DatabaseProperties.SCHEMA, ""));
        // Schema là identifier do người vận hành cấu hình, không lấy từ HTTP hoặc ghép tùy ý vào SQL.
        JdbcSchema.validate(schema);
        if (!url.startsWith("jdbc:postgresql://") || username.isBlank()) {
            throw new IllegalStateException(Env.DB_URL.key() + "/" + Env.DB_USER.key() + " are invalid");
        }
        // Production không được âm thầm dùng localhost/default khi thiếu Environment trên Render.
        if (production && (values.raw(Env.DB_URL) == null || values.raw(Env.DB_USER) == null || password.isBlank())) {
            throw new IllegalStateException("Production requires explicit " + Env.DB_URL.key() + "/USER/PASSWORD");
        }
        maximumPoolSize = values.number(Env.DB_POOL_SIZE, 4, 1, 10);
        acquisitionTimeoutMs = values.number(Env.DB_ACQUIRE_TIMEOUT_MS, 20000, 1000, 60000);
        connectTimeoutSeconds = values.number(Env.DB_CONNECT_TIMEOUT_SECONDS, 15, 1, 60);
        socketTimeoutSeconds = values.number(Env.DB_SOCKET_TIMEOUT_SECONDS, 30, 1, 300);
    }

    /** Chỉ nạp file mẫu không chứa secret trong classpath, Java không tự đọc .env. */
    private static Properties defaults() {
        Properties values = new Properties();
        try (InputStream input = DatabaseSettings.class.getResourceAsStream(DatabaseProperties.RESOURCE)) {
            if (input != null) {
                values.load(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read database configuration", exception);
        }
        return values;
    }
}
