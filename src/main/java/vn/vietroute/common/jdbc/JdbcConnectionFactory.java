package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Mượn connection từ DataSource; close trả lại pool, service chia sẻ cùng connection trong transaction. */
public final class JdbcConnectionFactory {

    private final DataSource source;

    public JdbcConnectionFactory(DataSource source) {
        this.source = source;
    }

    public Connection open() throws SQLException {
        Connection connection = source.getConnection();
        try {
            // Cố định search_path cho mọi connection, kể cả khi JDBC URL có currentSchema khác.
            connection.setSchema(JdbcSchema.NAME);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closing) {
                exception.addSuppressed(closing);
            }
            throw exception;
        }
    }
}
