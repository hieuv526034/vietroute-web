package vn.vietroute.common.config.database;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Mỗi connection JPA/JDBC bắt đầu transaction rồi đặt LOCAL schema; không dựa session của pooler. */
public final class SchemaDataSource extends DelegatingDataSource {

    private final JdbcSchema schema;

    public SchemaDataSource(DataSource target, JdbcSchema schema) {
        super(target);
        this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(super.getConnection(username, password));
    }

    private Connection prepare(Connection connection) throws SQLException {
        try {
            connection.setAutoCommit(false);
            schema.select(connection);
            return connection;
        } catch (SQLException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                failure.addSuppressed(rollback);
            }
            try {
                connection.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }
}
