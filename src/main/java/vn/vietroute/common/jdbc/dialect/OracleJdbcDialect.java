package vn.vietroute.common.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import org.hibernate.dialect.OracleDialect;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Chuẩn bị JDBC Thin cho Oracle 26ai; chưa bật SQL nghiệp vụ/migration PostgreSQL trên Oracle. */
public final class OracleJdbcDialect implements JdbcDialect {

    static final OracleJdbcDialect INSTANCE = new OracleJdbcDialect();

    private static final String APPLICATION_UNAVAILABLE =
        "Oracle 26ai mới có cấu hình JDBC; cần migration, repository SQL và kiểm thử Oracle trước khi chạy ứng dụng.";

    private OracleJdbcDialect() {}

    @Override
    public String vendor() {
        return "oracle";
    }

    @Override
    public String driverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    public String hibernateDialectClassName() {
        return OracleDialect.class.getName();
    }

    /** Oracle nhận timeout socket theo mili giây; không truyền tên thuộc tính riêng của pgJDBC. */
    @Override
    public Properties connectionProperties(
        int connectTimeoutSeconds,
        int socketTimeoutSeconds,
        String applicationName,
        boolean keepAlive
    ) {
        Properties properties = new Properties();
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", Integer.toString(connectTimeoutSeconds * 1000));
        properties.setProperty("oracle.net.OUTBOUND_CONNECT_TIMEOUT", Integer.toString(connectTimeoutSeconds * 1000));
        properties.setProperty("oracle.jdbc.ReadTimeout", Integer.toString(socketTimeoutSeconds * 1000));
        properties.setProperty("v$session.program", applicationName);
        properties.setProperty("oracle.net.keepAlive", Boolean.toString(keepAlive));
        return properties;
    }

    /** Chỉ nhận schema thường, viết hoa; không nhận quoted identifier hoặc tài khoản hệ thống. */
    @Override
    public void validateSchema(String name) {
        if (name == null || !name.matches(Regex.ORACLE_SCHEMA) || name.equals("SYS") || name.equals("SYSTEM")) {
            throw new IllegalStateException(
                Env.DB_SCHEMA.key() + " must be a private uppercase Oracle identifier (1-128 characters)"
            );
        }
    }

    @Override
    public void requireApplicationSupport() {
        throw new IllegalStateException(APPLICATION_UNAVAILABLE);
    }

    /** Không mô phỏng LOCAL search_path bằng trạng thái session Oracle chưa được kiểm thử. */
    @Override
    public void selectSchema(Connection connection, String name) throws SQLException {
        throw new SQLFeatureNotSupportedException(APPLICATION_UNAVAILABLE, "0A000");
    }
}
