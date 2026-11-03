package vn.vietroute.common.base;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Tiện ích JDBC thuần; callback đọc tự đóng connection, DAO trong transaction nhận connection riêng. */
public abstract class BaseRepository {

    protected final JdbcConnectionFactory connections;

    protected final JdbcTransactions transactions;

    protected final EntityManager entityManager;

    protected BaseRepository(JdbcConnectionFactory connections) {
        this.connections = connections;
        entityManager = null;
        transactions = new JdbcTransactions(connections);
    }

    /** Repo JPA dùng manager và transaction chung; JDBC batch nhận connection từ service. */
    protected BaseRepository(EntityManager manager) {
        connections = null;
        entityManager = manager;
        transactions = null;
    }

    /** Chuyển giá trị projection native, không thực thi hoặc che SQL của Repo. */
    public static long longValue(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    public static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public static boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value);
    }

    public static BigDecimal decimalValue(Object value) {
        return value == null || value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    public static Instant instantValue(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((Timestamp) value).toInstant();
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
