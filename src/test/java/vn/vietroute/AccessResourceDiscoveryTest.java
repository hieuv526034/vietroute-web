package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;
import vn.vietroute.support.NativeEndpointWebFixture;
import vn.vietroute.support.NativeSecurityFixture;

/** Thu thập endpoint thật và tôn trọng cờ sync/context sở hữu, thay đồng bộ action registry cũ. */
class AccessResourceDiscoveryTest {

    @Test
    void discoversRealMappingsAndKeepsPublicAndSelfServiceRoutesUnassignable() {
        try (var web = new NativeEndpointWebFixture()) {
            assertEquals("ENDPOINT", web.catalog.find("GET /api/v1/native-probe/{id}").getKind());
            assertEquals("MENU", web.catalog.find("GET /operations/native-probe").getKind());
            assertTrue(web.catalog.find("GET /api/v1/auth/csrf").isPublicEndpoint());
            assertTrue(web.catalog.find("GET /api/v1/auth/me").isSelfService());
            assertFalse(
                web.catalog
                    .assignable()
                    .stream()
                    .anyMatch(value -> value.isPublicEndpoint() || value.isSelfService())
            );
            assertEquals(3, web.catalog.assignable().size());
        }
    }

    @Test
    void synchronizationRunsOnlyForOwningServletAndHonorsExplicitDisabledFlag() {
        try (var web = new NativeEndpointWebFixture(); var other = new GenericApplicationContext()) {
            other.refresh();
            var count = new AtomicInteger();
            var transactions = new JdbcTransactions(null) {
                @Override
                public <T> T execute(SqlWork<T> work) {
                    count.incrementAndGet();
                    return null;
                }
            };
            var disabled = new AuthInitializer(
                null,
                null,
                settings(Map.of("VIETROUTE_ACCESS_SYNC_ENABLED", "false")),
                null,
                null,
                transactions,
                new RootConfig().objectMapper()
            );
            var listener = new EndpointAccess(null, null, disabled);
            listener.onApplicationEvent(new ContextRefreshedEvent(other));
            listener.onApplicationEvent(new ContextRefreshedEvent(web.context));
            assertEquals(0, count.get());
            var enabled = new EndpointAccess(
                null,
                null,
                new AuthInitializer(
                    null,
                    null,
                    settings(Map.of("VIETROUTE_ACCESS_SYNC_ENABLED", "true")),
                    null,
                    null,
                    transactions,
                    new RootConfig().objectMapper()
                )
            );
            enabled.onApplicationEvent(new ContextRefreshedEvent(other));
            assertEquals(0, count.get());
            enabled.onApplicationEvent(new ContextRefreshedEvent(web.context));
            assertEquals(1, count.get());
        }
    }

    @Test
    void synchronizationFollowsInitializerUnlessExplicitlyConfigured() {
        assertTrue(settings(Map.of("VIETROUTE_DB_INIT_ENABLED", "true")).isAccessSyncEnabled());
        assertFalse(
            settings(
                Map.of("VIETROUTE_DB_INIT_ENABLED", "true", "VIETROUTE_ACCESS_SYNC_ENABLED", "false")
            ).isAccessSyncEnabled()
        );
    }

    private static AppSettings settings(Map<String, String> overrides) {
        var values = new HashMap<>(overrides);
        values.put("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD", "TestOnly!2026Aa");
        values.put("VIETROUTE_BOOTSTRAP_ADMIN_EMAIL", "admin@example.invalid");
        return new AppSettings(values::get);
    }
}
