package vn.vietroute.common.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/**
 * Ranh giới cấu hình driver và chọn schema theo hệ DB; không tự dịch SQL repository/migration.
 * Chỉ bật một adapter khi đã có migration và regression transaction/quyền của hệ DB đó.
 */
public interface JdbcDialect {
    /** Adapter mặc định cho các caller PostgreSQL hiện hành. */
    static JdbcDialect postgresql() {
        return PostgreSqlDialect.INSTANCE;
    }

    /** Chọn adapter đã hỗ trợ trước khi tạo pool, không đưa URL/credential vào lỗi. */
    static JdbcDialect forUrl(String url) {
        if (url != null && url.startsWith("jdbc:postgresql://")) {
            return PostgreSqlDialect.INSTANCE;
        }
        if (url != null && url.startsWith("jdbc:oracle:")) {
            throw new IllegalStateException(
                "Oracle chưa được hỗ trợ: cần adapter SQL, migration và kiểm thử Oracle trước khi cấu hình " +
                    Env.DB_URL.key()
            );
        }
        throw new IllegalStateException(Env.DB_URL.key() + " uses an unsupported JDBC database");
    }

    String driverClassName();

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
