package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Service chia sẻ cùng connection cho mọi DAO trong một giao dịch; lỗi bất kỳ sẽ rollback. */
@Component @RequiredArgsConstructor
public class JdbcTransactions {
    private final JdbcConnectionFactory connections;
    public <T> T execute(BaseRepository.SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        } catch (SQLException exception) { throw BaseRepository.translate(exception); }
    }
}
