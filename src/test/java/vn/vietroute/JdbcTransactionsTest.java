package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
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
                assertFalse(state.autoCommit);
                assertTrue(state.schemaSelected);
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
        assertTrue(state.rolledBack.get());
        assertTrue(state.closed.get());
    }

    @Test
    void schemaSelectionRejectsAutoCommitOutsideBusinessTransaction() {
        State state = new State();
        var error = assertThrows(SQLException.class, () -> new JdbcSchema("isolated_schema").select(state.connection));
        assertEquals("25000", error.getSQLState());
        assertFalse(state.schemaSelected);
    }

    @Test
    void independentRepositoryUsesSameCommitAndRollbackBoundary() {
        State success = new State();
        assertEquals(
            "done",
            new Probe(success.factory()).run(connection -> {
                assertTrue(success.schemaSelected);
                return "done";
            })
        );
        assertTrue(success.committed.get());
        State failure = new State();
        assertThrows(BusinessException.class, () ->
            new Probe(failure.factory()).run(connection -> {
                throw new BusinessException(ErrorCode.CONFLICT);
            })
        );
        assertTrue(failure.rolledBack.get());
        assertTrue(failure.closed.get());
        assertFalse(failure.committed.get());
    }

    static class Probe extends BaseRepository {

        Probe(JdbcConnectionFactory connections) {
            super(connections);
        }

        <T> T run(SqlWork<T> work) {
            return read(work);
        }
    }

    /** DataSource/connection giả chỉ ghi nhận giao dịch, không mở socket hoặc đọc .env. */
    private static final class State {

        final AtomicBoolean committed = new AtomicBoolean(),
            rolledBack = new AtomicBoolean(),
            closed = new AtomicBoolean();

        boolean rejectSchema;

        boolean autoCommit = true;

        boolean schemaSelected;

        final Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAutoCommit" -> {
                        return autoCommit;
                    }
                    case "setAutoCommit" -> autoCommit = (boolean) args[0];
                    case "commit" -> committed.set(true);
                    case "rollback" -> rolledBack.set(true);
                    case "close" -> closed.set(true);
                    case "prepareStatement" -> {
                        assertFalse(autoCommit, "Phải bắt đầu transaction trước khi chọn schema");
                        assertEquals("SELECT pg_catalog.set_config('search_path', ?, true)", args[0]);
                        if (rejectSchema) {
                            throw new SQLException("test-only", "schema-test");
                        }
                        return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { PreparedStatement.class },
                            (statement, operation, parameters) -> {
                                if (operation.getName().equals("execute")) schemaSelected = true;
                                if (operation.getName().equals("setString")) {
                                    assertEquals("\"isolated_schema\", pg_temp", parameters[1]);
                                }
                                return operation.getName().equals("execute") ? false : null;
                            }
                        );
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
            return new JdbcConnectionFactory(source, new JdbcSchema("isolated_schema"));
        }
    }
}
