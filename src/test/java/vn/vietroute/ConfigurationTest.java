package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.config.AppSettings;
import vn.vietroute.common.config.DatabaseSettings;
import vn.vietroute.common.config.RootConfig;

/** Kiểm tra cấu hình lỗi dừng sớm và pool tạo lười; không truy cập database thật. */
class ConfigurationTest {

    @Test
    void rejectsUnknownEnvironmentAndProductionPlaceholder() {
        assertThrows(IllegalStateException.class, () -> new AppSettings(Map.of("VIETROUTE_ENV", "prodution")::get));
        assertThrows(IllegalStateException.class, () ->
            new AppSettings(
                Map.of(
                    "VIETROUTE_ENV",
                    "production",
                    "VIETROUTE_JWT_SECRET",
                    "replace_with_at_least_32_random_characters"
                )::get
            )
        );
    }

    @Test
    void productionRequiresExplicitDatabaseCredentialsWithoutEchoingThem() {
        assertThrows(IllegalStateException.class, () -> new DatabaseSettings(Map.<String, String>of()::get, true));
        var error = assertThrows(IllegalStateException.class, () ->
            new DatabaseSettings(
                Map.of(
                    "VIETROUTE_DB_URL",
                    "invalid-secret-url-never-echo",
                    "VIETROUTE_DB_USER",
                    "test-only",
                    "VIETROUTE_DB_PASSWORD",
                    "test-password-never-echo"
                )::get,
                true
            )
        );
        assertFalse(error.getMessage().contains("never-echo"));
        var settings = new DatabaseSettings(
            Map.of(
                "VIETROUTE_DB_URL",
                "jdbc:postgresql://localhost:5432/isolated_test",
                "VIETROUTE_DB_USER",
                "test-only",
                "VIETROUTE_DB_PASSWORD",
                "test-only"
            )::get,
            true
        );
        assertEquals(4, settings.getMaximumPoolSize());
    }

    @Test
    void invalidTimeoutOrPoolSizeCannotSilentlyUseDefaults() {
        for (String invalid : new String[] { "0", "11", "not-a-number", "" }) {
            assertThrows(IllegalStateException.class, () ->
                new DatabaseSettings(Map.of("VIETROUTE_DB_POOL_SIZE", invalid)::get, false)
            );
        }
        assertThrows(IllegalStateException.class, () ->
            new DatabaseSettings(Map.of("VIETROUTE_DB_SOCKET_TIMEOUT_SECONDS", "0")::get, false)
        );
        assertThrows(IllegalStateException.class, () ->
            new DatabaseSettings(Map.of("VIETROUTE_DB_ACQUIRE_TIMEOUT_MS", "999")::get, false)
        );
    }

    @Test
    void poolDoesNotOpenConnectionsUntilApplicationNeedsDatabase() {
        var settings = new DatabaseSettings(
            Map.of(
                "VIETROUTE_DB_URL",
                "jdbc:postgresql://127.0.0.1:1/unreachable_test",
                "VIETROUTE_DB_POOL_SIZE",
                "2"
            )::get,
            false
        );
        HikariDataSource pool = new RootConfig().dataSource(settings);
        try (pool) {
            assertNull(pool.getHikariPoolMXBean(), "Chưa tạo pool runtime hoặc mở kết nối ở bước cấu hình");
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(20000, pool.getConnectionTimeout());
            assertEquals(0, pool.getKeepaliveTime());
            assertEquals(15, pool.getDataSourceProperties().get("connectTimeout"));
            assertEquals(30, pool.getDataSourceProperties().get("socketTimeout"));
        }
        assertTrue(pool.isClosed());
    }

    @Test
    void primitiveNullIsRejectedInsteadOfBecomingPageZero() {
        var json = new RootConfig().objectMapper();
        assertThrows(MismatchedInputException.class, () -> json.readValue("{\"page\":null}", PageRequest.class));
    }
}
