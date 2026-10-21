package vn.vietroute;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.*;

/** Bảo đảm callback dùng cùng connection, commit khi thành công và rollback/đóng khi lỗi nghiệp vụ. */
class JdbcTransactionsTest {

    @Test
    void commitAndCloseSuccessfulTransaction() throws Exception {
        try (State state = new State()) {
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
    }

    @Test
    void rollbackAndCloseWithoutSwallowingBusinessError() throws Exception {
        try (State state = new State()) {
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
    }

    private static final class State implements AutoCloseable {

        final AtomicBoolean committed = new AtomicBoolean(),
            rolledBack = new AtomicBoolean(),
            closed = new AtomicBoolean();
        final Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "commit" -> committed.set(true);
                    case "rollback" -> rolledBack.set(true);
                    case "close" -> closed.set(true);
                    default -> {
                    }
                }
                return null;
            }
        );
        final String url = "jdbc:vietroute-test:" + java.util.UUID.randomUUID();
        final java.sql.Driver driver = new java.sql.Driver() {
            public boolean acceptsURL(String candidate) {
                return url.equals(candidate);
            }

            public Connection connect(String candidate, java.util.Properties info) {
                return acceptsURL(candidate) ? connection : null;
            }

            public java.sql.DriverPropertyInfo[] getPropertyInfo(String candidate, java.util.Properties info) {
                return new java.sql.DriverPropertyInfo[0];
            }

            public int getMajorVersion() {
                return 1;
            }

            public int getMinorVersion() {
                return 0;
            }

            public boolean jdbcCompliant() {
                return false;
            }

            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getGlobal();
            }
        };

        State() throws java.sql.SQLException {
            java.sql.DriverManager.registerDriver(driver);
        }

        JdbcConnectionFactory factory() {
            return new JdbcConnectionFactory(url, "unused", "unused");
        }

        @Override
        public void close() throws java.sql.SQLException {
            java.sql.DriverManager.deregisterDriver(driver);
        }
    }
}
