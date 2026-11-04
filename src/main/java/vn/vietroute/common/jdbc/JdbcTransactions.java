package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vietroute.common.base.BaseRepository;

/** Service chia sẻ cùng connection cho mọi DAO trong một giao dịch; lỗi bất kỳ sẽ rollback. */
@Component
public class JdbcTransactions {

    private final JdbcConnectionFactory connections;

    private final ObjectProvider<PlatformTransactionManager> transactionManagers;

    /** Constructor độc lập dành cho fixture JDBC, không tự tạo hoặc đọc cấu hình môi trường. */
    public JdbcTransactions(JdbcConnectionFactory connections) {
        this.connections = connections;
        this.transactionManagers = null;
    }

    @Autowired
    public JdbcTransactions(JdbcConnectionFactory connections, ObjectProvider<PlatformTransactionManager> managers) {
        this.connections = connections;
        this.transactionManagers = managers;
    }

    /**
     * REQUIRED qua template cả khi đang có transaction: lỗi ở callback con đánh dấu rollback-only,
     * kể cả caller bắt lỗi rồi tiếp tục. Callback không phụ thuộc proxy hoặc self-invocation.
     */
    public <T> T execute(BaseRepository.SqlWork<T> work) {
        PlatformTransactionManager manager = transactionManagers == null ? null : transactionManagers.getIfAvailable();
        if (manager != null) return new TransactionTemplate(manager).execute(status -> joined(work));
        if (TransactionSynchronizationManager.isActualTransactionActive()) return joined(work);
        try (Connection connection = connections.open()) {
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    // Giữ lỗi gốc làm nguyên nhân chính; lỗi rollback chỉ đính kèm để điều tra phía server.
                    exception.addSuppressed(rollback);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    /** Spring sở hữu commit/rollback; tuyệt đối không commit riêng JDBC khi JPA còn đang thay đổi entity. */
    private <T> T joined(BaseRepository.SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            return work.apply(connection);
        } catch (SQLException failure) {
            throw BaseRepository.translate(failure);
        }
    }
}
