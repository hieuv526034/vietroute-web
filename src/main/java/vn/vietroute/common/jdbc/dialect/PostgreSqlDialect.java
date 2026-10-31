package vn.vietroute.common.jdbc.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Adapter PostgreSQL hiện hành; giữ LOCAL search_path an toàn khi dùng transaction pooler. */
public final class PostgreSqlDialect implements JdbcDialect {

    static final PostgreSqlDialect INSTANCE = new PostgreSqlDialect();

    private PostgreSqlDialect() {}

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public Properties connectionProperties(
        int connectTimeoutSeconds,
        int socketTimeoutSeconds,
        String applicationName,
        boolean keepAlive
    ) {
        Properties properties = new Properties();
        properties.put("connectTimeout", connectTimeoutSeconds);
        properties.put("socketTimeout", socketTimeoutSeconds);
        properties.put("ApplicationName", applicationName);
        if (keepAlive) {
            properties.put("tcpKeepAlive", true);
        }
        return properties;
    }

    @Override
    public void validateSchema(String name) {
        if (
            name == null ||
            !name.matches(Regex.SCHEMA) ||
            name.startsWith("pg_") ||
            name.equals("public") ||
            name.equals("information_schema")
        ) {
            throw new IllegalStateException(
                Env.DB_SCHEMA.key() + " must be a private lowercase PostgreSQL identifier (1-63 characters)"
            );
        }
    }

    /** pg_catalog được ưu tiên ngầm; pg_temp ở cuối, không có public/$user fallback. */
    @Override
    public void selectSchema(Connection connection, String name) throws SQLException {
        validateSchema(name);
        if (connection.getAutoCommit()) {
            throw new SQLException("Chọn schema cần transaction chứa cả truy vấn nghiệp vụ", "25000");
        }
        try (
            PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.set_config('search_path', ?, true)"
            )
        ) {
            statement.setString(1, "\"" + name + "\", pg_temp");
            statement.execute();
        }
    }
}
