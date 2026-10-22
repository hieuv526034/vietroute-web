package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Mượn connection từ DataSource; close trả lại pool, service chia sẻ cùng connection trong transaction. */
public final class JdbcConnectionFactory {

    private final DataSource source;

    private final JdbcSchema schema;

    public JdbcConnectionFactory(DataSource source, JdbcSchema schema) {
        this.source = source;
        this.schema = schema;
    }

    public Connection open() throws SQLException {
        Connection connection = source.getConnection();
        try {
            schema.select(connection);
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
