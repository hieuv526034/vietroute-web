package vn.vietroute.common.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Creates a new JDBC connection. Transaction callers must share that connection across DAOs. */
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
                setting("VIETROUTE_DB_PASSWORD", ""));
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
