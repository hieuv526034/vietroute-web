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
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.database.DatabaseConfig;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.config.monitoring.MonitoringConfig;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.dialect.JdbcDialect;

/** Kiểm tra cấu hình lỗi dừng sớm và pool tạo lười; không truy cập database thật. */
class ConfigurationTest {

    @Test
    void unsupportedDatabaseFailsBeforePoolWithoutLeakingConnectionDetails() {
        for (String url : new String[] {
            "jdbc:oracle:thin:private-user/private-password@private-host:1521/private-service",
            "jdbc:mysql://private-host/private-database?password=private-password",
        }) {
            var error = assertThrows(IllegalStateException.class, () ->
                new DatabaseSettings(Map.of("VIETROUTE_DB_URL", url)::get, false)
            );
            assertFalse(error.getMessage().contains("private-"));
            assertTrue(error.getMessage().contains("VIETROUTE_DB_URL"));
            if (url.startsWith("jdbc:oracle:")) {
                assertTrue(error.getMessage().contains("Oracle"));
                assertTrue(error.getMessage().contains("migration"));
            }
        }
    }

    @Test
    void dialectKeepsBusinessAndObservationConnectionOptionsIndependent() {
        var dialect = JdbcDialect.forUrl("jdbc:postgresql://127.0.0.1:1/isolated_test");
        assertEquals("org.postgresql.Driver", dialect.driverClassName());
        var business = dialect.connectionProperties(15, 30, "vietroute-web", true);
        var observation = dialect.connectionProperties(2, 2, "vietroute-api-journal", false);
        assertEquals(15, business.get("connectTimeout"));
        assertEquals(30, business.get("socketTimeout"));
        assertEquals(true, business.get("tcpKeepAlive"));
        assertEquals(2, observation.get("connectTimeout"));
        assertEquals(2, observation.get("socketTimeout"));
        assertEquals("vietroute-api-journal", observation.get("ApplicationName"));
        assertFalse(observation.containsKey("tcpKeepAlive"));
        business.put("socketTimeout", 99);
        assertEquals(2, observation.get("socketTimeout"));
        assertEquals(30, dialect.connectionProperties(15, 30, "vietroute-web", true).get("socketTimeout"));
    }

    @Test
    void bootstrapEmailComesFromConfigurationAndRejectsInvalidValues() {
        assertEquals("hieuv526034@gmail.com", new AppSettings(Map.<String, String>of()::get).getBootstrapEmail());
        assertEquals(
            "owner@example.com",
            new AppSettings(Map.of("VIETROUTE_BOOTSTRAP_ADMIN_EMAIL", "OWNER@example.com")::get).getBootstrapEmail()
        );
        for (String invalid : new String[] { "", "invalid", "a@b\nInjected" }) {
            assertThrows(IllegalStateException.class, () ->
                new AppSettings(Map.of("VIETROUTE_BOOTSTRAP_ADMIN_EMAIL", invalid)::get)
            );
        }
    }

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
        var registry = new MonitoringConfig().meterRegistry();
        HikariDataSource pool = new DatabaseConfig().dataSource(settings, registry);
        try (pool) {
            assertNull(pool.getHikariPoolMXBean(), "Chưa tạo pool runtime hoặc mở kết nối ở bước cấu hình");
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(20000, pool.getConnectionTimeout());
            assertEquals(0, pool.getKeepaliveTime());
            assertEquals(15, pool.getDataSourceProperties().get("connectTimeout"));
            assertEquals(30, pool.getDataSourceProperties().get("socketTimeout"));
        } finally {
            registry.close();
        }
        assertTrue(pool.isClosed());
    }

    @Test
    void primitiveNullIsRejectedInsteadOfBecomingPageZero() {
        var json = new RootConfig().objectMapper();
        assertThrows(MismatchedInputException.class, () -> json.readValue("{\"page\":null}", PageRequest.class));
    }

    @Test
    void schemaUsesOneConfigAndRejectsSearchPathOrSqlInjection() {
        var settings = new DatabaseSettings(Map.of("VIETROUTE_DB_SCHEMA", "project_2026")::get, false);
        assertEquals("project_2026", settings.getSchema());
        assertEquals("\"project_2026\"", new JdbcSchema(settings.getSchema()).quoted());
        for (String invalid : new String[] {
            "",
            "public",
            "pg_temp",
            "information_schema",
            "a,b",
            "a;drop schema public",
            "UPPER",
            "a\"b",
            "a".repeat(64),
        }) {
            assertThrows(IllegalStateException.class, () ->
                new DatabaseSettings(Map.of("VIETROUTE_DB_SCHEMA", invalid)::get, false)
            );
        }
    }
}
