package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.monitoring.filter.HttpMetricsFilter;
import vn.vietroute.common.monitoring.repository.MonitoringRepository;
import vn.vietroute.common.monitoring.repository.MonitoringRepository.QueueSample;
import vn.vietroute.common.monitoring.service.ApplicationMetrics;
import vn.vietroute.common.monitoring.service.DatabaseProbe;
import vn.vietroute.support.AccessTestConfig;

/** Kiểm quyền metrics, lỗi trước MVC, giới hạn cardinality và mất DB; không kết nối database thật. */
class MonitoringFoundationTest {

    private static final String TOKEN = "monitoring-test-token-0123456789-abcdef";

    @Configuration
    static class TestSettings {

        @Bean
        public MonitoringSettings monitoringSettings() {
            return new MonitoringSettings(Map.of("VIETROUTE_METRICS_TOKEN", TOKEN)::get);
        }
    }

    private AnnotationConfigWebApplicationContext context(boolean token) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
        context.register(RootConfig.class, AccessTestConfig.class, WebConfig.class);
        if (token) {
            context.register(TestSettings.class);
        }
        context.refresh();
        return context;
    }

    @Test
    void scrapeUsesSeparateTokenWithoutDatabaseAndDoesNotAuthorizeOtherRoutes() throws Exception {
        try (var context = context(true)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            assertEquals(401, mvc.perform(get(Routes.METRICS)).andReturn().getResponse().getStatus());
            assertEquals(
                401,
                mvc
                    .perform(get(Routes.METRICS).cookie(new Cookie(Http.AUTH_COOKIE, "invalid")))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                401,
                mvc
                    .perform(get(Routes.METRICS).header("Authorization", "Bearer wrong"))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var scrape = mvc
                .perform(get(Routes.METRICS).header("Authorization", "Bearer " + TOKEN))
                .andReturn()
                .getResponse();
            assertEquals(200, scrape.getStatus());
            assertTrue(scrape.getContentAsString().contains("jvm_memory_used_bytes"));
            assertTrue(scrape.getContentAsString().contains("vietroute_http_duration_seconds_bucket"));
            assertFalse(scrape.getContentAsString().contains(TOKEN));
            assertEquals("no-store", scrape.getHeader("Cache-Control"));
            assertEquals(
                405,
                mvc
                    .perform(post(Routes.METRICS).header("Authorization", "Bearer " + TOKEN))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                401,
                mvc
                    .perform(get(Routes.SYSTEM_RUNTIME).header("Authorization", "Bearer " + TOKEN))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            // Route con không thuộc chain scrape: áp chính sách trang thông thường, chuyển về login.
            var child = mvc
                .perform(get(Routes.METRICS + "/child"))
                .andReturn()
                .getResponse();
            assertEquals(302, child.getStatus());
            assertTrue(child.getRedirectedUrl().endsWith(Routes.LOGIN));
            assertNull(
                context.getBean(HikariDataSource.class).getHikariPoolMXBean(),
                "Scrape không được mở connection kể cả cookie sai"
            );
        }
    }

    @Test
    void disabledEndpointIsClosedAndUiAndRuntimeRequireSystemRead() throws Exception {
        try (var context = context(false)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            assertEquals(404, mvc.perform(get(Routes.METRICS)).andReturn().getResponse().getStatus());
            var denied = UsernamePasswordAuthenticationToken.authenticated(
                "viewer",
                null,
                List.of(new SimpleGrantedAuthority("CATALOG_READ"))
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.SYSTEM_RUNTIME).with(authentication(denied)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.MONITORING_PAGE).with(authentication(denied)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var allowed = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                null,
                List.of(new SimpleGrantedAuthority("SYSTEM_READ"), new SimpleGrantedAuthority("SYSTEM_VIEW"))
            );
            var runtime = mvc
                .perform(get(Routes.SYSTEM_RUNTIME).with(authentication(allowed)))
                .andReturn()
                .getResponse();
            assertEquals(200, runtime.getStatus());
            assertTrue(runtime.getContentAsString().contains("heapUsedBytes"));
            for (String locale : List.of("vi", "en")) {
                var page = mvc
                    .perform(get(Routes.MONITORING_PAGE).param("lang", locale).with(authentication(allowed)))
                    .andReturn()
                    .getResponse();
                assertEquals(200, page.getStatus());
                assertTrue(page.getContentAsString().contains("monitoring-dashboard"));
            }
            SecurityContextHolder.clearContext();
            assertThrows(AccessDeniedException.class, () -> {
                SecurityContextHolder.getContext().setAuthentication(denied);
                context.getBean(ApplicationMetrics.class).snapshot();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void requestsRejectedBeforeControllerAreCountedAndPathsCannotCreateUnlimitedMeters() throws Exception {
        try (var context = context(true)) {
            var metrics = context.getBean(ApplicationMetrics.class);
            var registry = context.getBean(PrometheusMeterRegistry.class);
            var filter = new HttpMetricsFilter(metrics);
            for (int index = 0; index < 200; index++) {
                var request = new MockHttpServletRequest("GET", "/unknown-" + index);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (input, output) -> response.setStatus(429));
            }
            var bad = new MockHttpServletRequest("POST", Routes.AUTH_LOGIN);
            assertThrows(ServletException.class, () ->
                filter.doFilter(bad, new MockHttpServletResponse(), (input, output) -> {
                    throw new ServletException("test");
                })
            );
            assertEquals(0, registry.get("vietroute.http.active").gauge().value());
            assertEquals(201, registry.get("vietroute.http.duration").timer().count());
            assertEquals(200, registry.get("vietroute.http.limited").functionCounter().count());
            assertEquals(1, registry.get("vietroute.http.errors").functionCounter().count());
            assertEquals(2, registry.find("vietroute.http.responses").counters().size());
            assertFalse(registry.scrape().contains("unknown-"));
        }
    }

    @Test
    void probeReportsFailureWithoutReturningOldQueueAsFreshAndCanRecover() {
        var settings = new MonitoringSettings(Map.of("VIETROUTE_MONITORING_DB_ENABLED", "true")::get);
        var repo = new MonitoringRepository(null) {
            private int calls;

            @Override
            public QueueSample sample() throws SQLException {
                if (++calls == 2) {
                    throw new SQLException("must-not-log-credentials");
                }
                return new QueueSample(Instant.parse("2026-10-22T00:00:00Z"), 3, 1, 12);
            }
        };
        var probe = new DatabaseProbe(settings, repo);
        assertNull(probe.state().available());
        probe.sample();
        assertEquals(true, probe.state().available());
        assertEquals(3, probe.state().queue().queued());
        probe.sample();
        assertEquals(false, probe.state().available());
        assertNull(probe.state().queue());
        assertEquals(Instant.parse("2026-10-22T00:00:00Z"), probe.state().lastSuccess());
        probe.sample();
        assertEquals(true, probe.state().available());
        var disabled = new DatabaseProbe(new MonitoringSettings(key -> null), null);
        disabled.start();
        disabled.sample();
        assertFalse(disabled.isRunning());
        assertNull(disabled.state().available());
    }

    @Test
    void badMonitoringSettingsNeverEchoSecrets() {
        var error = assertThrows(IllegalStateException.class, () ->
            new MonitoringSettings(Map.of("VIETROUTE_METRICS_TOKEN", "secret-short")::get)
        );
        assertFalse(error.getMessage().contains("secret-short"));
        assertFalse(new MonitoringSettings(key -> null).accepts("Bearer " + TOKEN));
        assertFalse(
            new MonitoringSettings(Map.of("VIETROUTE_METRICS_TOKEN", TOKEN)::get).accepts("Bearer " + TOKEN + "x")
        );
    }
}
