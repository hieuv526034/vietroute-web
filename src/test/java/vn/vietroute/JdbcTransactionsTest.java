package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcTransactions;

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
        final String url = "jdbc:vietroute-test:" + UUID.randomUUID();
        final Driver driver = new Driver() {
            public boolean acceptsURL(String candidate) {
                return url.equals(candidate);
            }

            public Connection connect(String candidate, Properties info) {
                return acceptsURL(candidate) ? connection : null;
            }

            public DriverPropertyInfo[] getPropertyInfo(String candidate, Properties info) {
                return new DriverPropertyInfo[0];
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

            public Logger getParentLogger() {
                return Logger.getGlobal();
            }
        };

        State() throws SQLException {
            DriverManager.registerDriver(driver);
        }

        JdbcConnectionFactory factory() {
            return new JdbcConnectionFactory(url, "unused", "unused");
        }

        @Override
        public void close() throws SQLException {
            DriverManager.deregisterDriver(driver);
        }
    }
}
