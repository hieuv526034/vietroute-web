package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Bảo đảm callback dùng cùng connection, commit khi thành công và rollback/đóng khi lỗi nghiệp vụ. */
class JdbcTransactionsTest {

    @Test
    void commitAndCloseSuccessfulTransaction() throws Exception {
        State state = new State();
        JdbcTransactions transactions = new JdbcTransactions(state.factory());
        assertEquals(
            "done",
            transactions.execute(connection -> {
                assertSame(state.connection, connection);
                return "done";
            })
        );
        assertTrue(state.committed.get());
        assertFalse(state.rolledBack.get());
        assertTrue(state.closed.get());
    }

    @Test
    void rollbackAndCloseWithoutSwallowingBusinessError() throws Exception {
        State state = new State();
        JdbcTransactions transactions = new JdbcTransactions(state.factory());
        var error = assertThrows(BusinessException.class, () ->
            transactions.execute(connection -> {
                throw new BusinessException(ErrorCode.CONFLICT);
            })
        );
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertFalse(state.committed.get());
        assertTrue(state.rolledBack.get());
        assertTrue(state.closed.get());
    }

    @Test
    void rollbackBeforeReturningConnectionToPoolEvenOnJvmError() {
        State state = new State();
        AssertionError original = new AssertionError("test-only");
        AssertionError error = assertThrows(AssertionError.class, () ->
            new JdbcTransactions(state.factory()).execute(connection -> {
                throw original;
            })
        );
        assertSame(original, error);
        assertTrue(state.rolledBack.get());
        assertTrue(state.closed.get());
        assertFalse(state.committed.get());
    }

    @Test
    void schemaFailureReturnsBorrowedConnectionAndPropagatesSqlError() {
        State state = new State();
        state.rejectSchema = true;
        SQLException error = assertThrows(SQLException.class, () -> state.factory().open());
        assertEquals("schema-test", error.getSQLState());
        assertTrue(state.closed.get());
    }

    /** DataSource/connection giả chỉ ghi nhận giao dịch, không mở socket hoặc đọc .env. */
    private static final class State {

        final AtomicBoolean committed = new AtomicBoolean(),
            rolledBack = new AtomicBoolean(),
            closed = new AtomicBoolean();

        boolean rejectSchema;

        final Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "commit" -> committed.set(true);
                    case "rollback" -> rolledBack.set(true);
                    case "close" -> closed.set(true);
                    case "setSchema" -> {
                        assertEquals("vietroute", args[0]);
                        if (rejectSchema) {
                            throw new SQLException("test-only", "schema-test");
                        }
                    }
                    default -> {
                    }
                }
                return null;
            }
        );

        JdbcConnectionFactory factory() {
            DataSource source = (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> connection
            );
            return new JdbcConnectionFactory(source);
        }
    }
}
