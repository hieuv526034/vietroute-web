package vn.vietroute.common.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/**
 * Ranh giới cấu hình driver và chọn schema theo hệ DB; không tự dịch SQL repository/migration.
 * Cấu hình kết nối không thay thế migration và regression transaction/quyền của hệ DB đó.
 */
public interface JdbcDialect {
    /** Adapter mặc định cho các caller PostgreSQL hiện hành. */
    static JdbcDialect postgresql() {
        return PostgreSqlDialect.INSTANCE;
    }

    /** Chỉ nhận tên cố định; không dùng cấu hình từ bên ngoài làm đường dẫn classpath. */
    static JdbcDialect forVendor(String vendor) {
        if ("postgresql".equals(vendor)) {
            return PostgreSqlDialect.INSTANCE;
        }
        if ("oracle".equals(vendor)) {
            return OracleJdbcDialect.INSTANCE;
        }
        throw new IllegalStateException(Env.DB_VENDOR.key() + " must be postgresql or oracle");
    }

    /** Chọn cấu hình driver trước khi tạo pool, không đưa URL/credential vào lỗi. */
    static JdbcDialect forUrl(String url) {
        if (url != null && url.startsWith("jdbc:postgresql://")) {
            return PostgreSqlDialect.INSTANCE;
        }
        if (url != null && url.startsWith("jdbc:oracle:thin:@")) {
            return OracleJdbcDialect.INSTANCE;
        }
        throw new IllegalStateException(Env.DB_URL.key() + " uses an unsupported JDBC database");
    }

    String driverClassName();

    String vendor();

    String hibernateDialectClassName();

    /** Chặn runtime chưa có migration/repository tương ứng, kể cả khi các cờ init đã tắt. */
    default void requireApplicationSupport() {}

    Properties connectionProperties(
        int connectTimeoutSeconds,
        int socketTimeoutSeconds,
        String applicationName,
        boolean keepAlive
    );

    void validateSchema(String name);

    /** Chọn schema trong ranh giới transaction của caller; adapter không được tự commit. */
    void selectSchema(Connection connection, String name) throws SQLException;
}
