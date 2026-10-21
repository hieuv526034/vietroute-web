package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Tiện ích JDBC thuần; callback đọc tự đóng connection, DAO trong transaction nhận connection riêng. */
public abstract class BaseRepository {

    protected final JdbcConnectionFactory connections;

    protected BaseRepository(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Truy vấn độc lập tự đóng connection; DAO đang trong transaction phải dùng connection do service truyền vào. */
    protected <T> T read(SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            return work.apply(connection);
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    public static BusinessException translate(SQLException error) {
        // SQLState chuẩn; không đính kèm SQL/parameter/mật khẩu vào lỗi trả về.
        return new BusinessException(
            "23505".equals(error.getSQLState()) ? ErrorCode.CONFLICT : ErrorCode.DATABASE_UNAVAILABLE
        );
    }
}
