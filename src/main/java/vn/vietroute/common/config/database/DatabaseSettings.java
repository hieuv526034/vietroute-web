package vn.vietroute.common.config.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.DatabaseProperties;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.dialect.JdbcDialect;

/** Cấu hình JDBC/pool có giới hạn; không tạo toString để tránh ghi credentials ra log. */
@Getter
public final class DatabaseSettings {

    /** Cấu hình driver được chọn theo vendor; thiếu vendor thì suy từ URL để giữ cấu hình cũ. */
    private final JdbcDialect dialect;

    /** JDBC URL của đúng profile; production không được dùng localhost dự phòng. */
    private final String url;

    /** Tài khoản kết nối DB; không phải tài khoản đăng nhập ứng dụng. */
    private final String username;

    /** Mật khẩu DB chỉ giữ trong settings riêng, không serialize/log. */
    private final String password;

    /** Schema được validate theo vendor, không tự đổi chữ hoa/thường. */
    private final String schema;

    /** Trần connection nghiệp vụ trên mỗi node, mặc định 4 connection nghiệp vụ mỗi node. */
    private final int maximumPoolSize;

    /** Mili giây chờ mượn connection từ pool, mặc định 20000; hết hạn trả lỗi dữ liệu. */
    private final int acquisitionTimeoutMs;

    /** Giây tối đa tạo kết nối TCP/SSL, mặc định 15. */
    private final int connectTimeoutSeconds;

    /** Giây chờ đọc dữ liệu, mặc định 30; adapter đổi sang đơn vị của driver. */
    private final int socketTimeoutSeconds;

    public DatabaseSettings(Function<String, String> source, boolean production) {
        ConfigValues values = new ConfigValues(source);
        Properties defaults = defaults(DatabaseProperties.RESOURCE);
        String explicitUrl = values.raw(Env.DB_URL);
        String vendor = values.raw(Env.DB_VENDOR);
        dialect =
            vendor == null
                ? explicitUrl == null
                    ? JdbcDialect.forVendor(defaults.getProperty(DatabaseProperties.VENDOR))
                    : JdbcDialect.forUrl(explicitUrl)
                : JdbcDialect.forVendor(vendor);
        defaults.putAll(defaults(DatabaseProperties.PROFILE_DIRECTORY + dialect.vendor() + ".properties"));
        url = values.text(Env.DB_URL, defaults.getProperty(DatabaseProperties.URL, ""));
        if (JdbcDialect.forUrl(url) != dialect) {
            throw new IllegalStateException(Env.DB_VENDOR.key() + " does not match " + Env.DB_URL.key());
        }
        username = values.text(Env.DB_USER, defaults.getProperty(DatabaseProperties.USERNAME, ""));
        password = values.text(Env.DB_PASSWORD, defaults.getProperty(DatabaseProperties.PASSWORD, ""));
        schema = values.text(Env.DB_SCHEMA, defaults.getProperty(DatabaseProperties.SCHEMA, ""));
        // Schema là identifier do người vận hành cấu hình, không lấy từ HTTP hoặc ghép tùy ý vào SQL.
        dialect.validateSchema(schema);
        if (username.isBlank()) {
            throw new IllegalStateException(Env.DB_URL.key() + "/" + Env.DB_USER.key() + " are invalid");
        }
        // Production không được âm thầm dùng localhost/default khi thiếu Environment trên Render.
        if (
            production &&
            (values.raw(Env.DB_URL) == null ||
                values.raw(Env.DB_USER) == null ||
                values.raw(Env.DB_PASSWORD) == null ||
                password.isBlank())
        ) {
            throw new IllegalStateException("Production requires explicit " + Env.DB_URL.key() + "/USER/PASSWORD");
        }
        maximumPoolSize = values.number(Env.DB_POOL_SIZE, 4, 1, 10);
        acquisitionTimeoutMs = values.number(Env.DB_ACQUIRE_TIMEOUT_MS, 20000, 1000, 60000);
        connectTimeoutSeconds = values.number(Env.DB_CONNECT_TIMEOUT_SECONDS, 15, 1, 60);
        socketTimeoutSeconds = values.number(Env.DB_SOCKET_TIMEOUT_SECONDS, 30, 1, 300);
    }

    /** Chỉ nạp file mẫu không chứa secret trong classpath, Java không tự đọc .env. */
    private static Properties defaults(String resource) {
        Properties values = new Properties();
        try (InputStream input = DatabaseSettings.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing database configuration resource");
            }
            values.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read database configuration", exception);
        }
        return values;
    }
}
