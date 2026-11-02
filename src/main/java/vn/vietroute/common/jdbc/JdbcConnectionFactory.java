package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Mượn connection và bắt đầu transaction trước khi chọn schema, tương thích pooler luân chuyển backend. */
public final class JdbcConnectionFactory {

    private final DataSource source;

    private final JdbcSchema schema;

    public JdbcConnectionFactory(DataSource source, JdbcSchema schema) {
        this.source = source;
        this.schema = schema;
    }

    public Connection open() throws SQLException {
        Connection connection = source.getConnection();
        // JPA đã mượn connection qua SchemaDataSource và chọn LOCAL schema trong cùng transaction.
        // Callback JDBC không đổi trạng thái hoặc phát lại SET search_path mỗi lần gọi Repo.
        if (
            source instanceof TransactionAwareDataSourceProxy &&
            TransactionSynchronizationManager.isActualTransactionActive()
        ) return connection;
        try {
            // search_path và toàn bộ SQL của caller phải cùng transaction/backend của PgBouncer.
            // Caller ghi phải commit; caller chỉ đọc đóng connection để pool rollback transaction còn mở.
            connection.setAutoCommit(false);
            schema.select(connection);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                exception.addSuppressed(rollback);
            }
            try {
                connection.close();
            } catch (SQLException closing) {
                exception.addSuppressed(closing);
            }
            throw exception;
        }
    }
}
