package vn.vietroute.common.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Tạo connection JDBC mới; service phải chia sẻ cùng connection cho các DAO trong một transaction. */
public final class JdbcConnectionFactory {

    private final String url;

    private final String username;

    private final String password;

    public JdbcConnectionFactory(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static JdbcConnectionFactory fromEnvironment() {
        Properties defaults = new Properties();
        try (InputStream input = JdbcConnectionFactory.class.getResourceAsStream("/database.properties")) {
            if (input != null) {
                defaults.load(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read database configuration", exception);
        }
        return new JdbcConnectionFactory(
            setting("VIETROUTE_DB_URL", defaults.getProperty("db.url")),
            setting("VIETROUTE_DB_USER", defaults.getProperty("db.username")),
            setting("VIETROUTE_DB_PASSWORD", "")
        );
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    public Connection open() throws SQLException {
        // Tomcat khởi tạo DriverManager trước khi nhìn thấy driver trong WEB-INF/lib của ứng dụng.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("PostgreSQL JDBC driver is missing from the runtime", exception);
        }
        return DriverManager.getConnection(url, username, password);
    }
}
