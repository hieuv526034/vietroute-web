package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Tiện ích JDBC thuần; callback đọc tự đóng connection, DAO trong transaction nhận connection riêng. */
public abstract class BaseRepository {

    protected final JdbcConnectionFactory connections;

    private final JdbcTransactions transactions;

    protected BaseRepository(JdbcConnectionFactory connections) {
        this.connections = connections;
        transactions = new JdbcTransactions(connections);
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Callback độc lập dùng cùng transaction với schema; dùng chung luồng commit/rollback, không dựa vào close ngầm. */
    protected <T> T read(SqlWork<T> work) {
        return transactions.execute(work);
    }

    public static BusinessException translate(SQLException error) {
        // SQLState chuẩn; không đính kèm SQL/parameter/mật khẩu vào lỗi trả về.
        BusinessException translated = new BusinessException(
            "23505".equals(error.getSQLState()) ? ErrorCode.CONFLICT : ErrorCode.DATABASE_UNAVAILABLE
        );
        // Giữ nguyên nhân nội bộ để chẩn đoán SQLState; không đưa message/SQL/parameter ra HTTP.
        translated.initCause(error);
        return translated;
    }
}
