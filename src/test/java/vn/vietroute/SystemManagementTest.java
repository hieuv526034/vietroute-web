package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.AccessUserSearchRequest;
import vn.vietroute.auth.dto.response.AccessUserResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.config.integration.JobSettings;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.config.monitoring.ActivitySettings;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.monitoring.service.SystemConfigurationService;
import vn.vietroute.support.AccessTestConfig;

/** Kiểm quyền UI/API và whitelist cấu hình trước khi mở DB; SQL integration chạy trên PostgreSQL CI tạm. */
class SystemManagementTest {

    @Configuration
    static class IsolatedUsers {

        @Bean
        @Primary
        UserRepository users(JdbcConnectionFactory connections) {
            return new UserRepository(connections) {
                @Override
                public PageResponse<AccessUserResponse> pageForAccess(AccessUserSearchRequest page) {
                    return PageResponse.of(List.of(), page, 0);
                }
            };
        }
    }

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
        context.register(RootConfig.class, AccessTestConfig.class, WebConfig.class, IsolatedUsers.class);
        context.refresh();
        return context;
    }

    private UsernamePasswordAuthenticationToken actor(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(
                2L,
                "isolated",
                Set.of(List.of(authorities).contains("ROLE_ADMIN") ? Role.ADMIN : Role.OPERATOR),
                0
            ),
            null,
            AccessTestConfig.upgradedAuthorities(authorities)
        );
    }

    @Test
    void managementPagesRequireTheirOwnPermissionAndRenderBothLocalesWithoutDb() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            Map<String, String> pages = Map.of(
                Routes.JOBS_PAGE,
                "JOB_READ",
                Routes.ACCESS_PAGE,
                "ACCESS_READ",
                Routes.CONFIGURATION_PAGE,
                "SYSTEM_READ"
            );
            for (var entry : pages.entrySet()) {
                assertEquals(302, mvc.perform(get(entry.getKey())).andReturn().getResponse().getStatus());
                assertEquals(
                    403,
                    mvc
                        .perform(get(entry.getKey()).with(authentication(actor("CATALOG_READ"))))
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
                for (String locale : List.of("vi", "en")) {
                    var response = mvc
                        .perform(
                            get(entry.getKey())
                                .param("lang", locale)
                                .with(authentication(actor(entry.getValue())))
                        )
                        .andReturn()
                        .getResponse();
                    assertEquals(200, response.getStatus());
                    String html = response.getContentAsString();
                    assertTrue(html.contains("data-operations="));
                    assertTrue(html.contains("ops-controls"));
                    assertFalse(html.contains("th:text="));
                    assertFalse(html.contains("??ui.ops"));
                }
            }
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void configApiIsReadOnlyAndProtectedAndDocumented() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            assertEquals(401, mvc.perform(get(Routes.SYSTEM_CONFIGURATION)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.SYSTEM_CONFIGURATION).with(authentication(actor("ACCESS_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                405,
                mvc
                    .perform(
                        post(Routes.SYSTEM_CONFIGURATION)
                            .with(csrf())
                            .with(authentication(actor("SYSTEM_READ")))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var result = mvc
                .perform(get(Routes.SYSTEM_CONFIGURATION).with(authentication(actor("SYSTEM_READ"))))
                .andReturn()
                .getResponse();
            assertEquals(200, result.getStatus());
            var data = context.getBean(ObjectMapper.class).readTree(result.getContentAsString()).get("data");
            assertTrue(data.get("readOnly").asBoolean());
            assertEquals(4, data.path("database").size());
            assertTrue(data.path("database").has("maximumPoolSize"));
            assertFalse(data.path("database").has("url"));
            assertEquals(
                403,
                mvc
                    .perform(post(Routes.SYSTEM_CONFIGURATION).with(authentication(actor("SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var document = context
                .getBean(ObjectMapper.class)
                .readTree(mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse().getContentAsString());
            assertTrue(document.get("paths").has(Routes.SYSTEM_CONFIGURATION));
            assertTrue(document.get("paths").has(Routes.ACCESS + Routes.USERS_ACTION));
            assertTrue(document.get("components").get("schemas").has("AccessUserResponse"));
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void delegatedAccessReadCanEnumerateAccountsAndDirectServicesEnforcePermissions() {
        try (var context = context()) {
            var access = context.getBean(AccessService.class);
            var configuration = context.getBean(SystemConfigurationService.class);
            for (var authentication : List.of(actor("ROLE_ADMIN"), actor("ACCESS_ASSIGN"))) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                assertThrows(AccessDeniedException.class, () -> access.users(new AccessUserSearchRequest()));
            }
            SecurityContextHolder.getContext().setAuthentication(actor("ACCESS_READ"));
            assertEquals(0, access.users(new AccessUserSearchRequest()).totalElements());
            assertThrows(AccessDeniedException.class, configuration::read);
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void invalidFiltersAndPaginationFailBeforeAnyDatabaseRead() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var admin = actor("ROLE_ADMIN", "ACCESS_READ", "JOB_READ");
            var filter = mvc
                .perform(
                    get(Routes.ACCESS + Routes.USERS_ACTION)
                        .param("username", "%private_")
                        .with(authentication(admin))
                )
                .andReturn()
                .getResponse();
            assertEquals(400, filter.getStatus());
            assertTrue(filter.getContentAsString().contains("validation.usernameSearch"));
            assertEquals(
                400,
                mvc
                    .perform(get(Routes.JOBS).param("status", "INVALID").with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        get(Routes.ACCESS + Routes.USERS_ACTION)
                            .param("size", "101")
                            .with(authentication(admin))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void configWhitelistNeverSerializesConfiguredSecretsOrEndpoints() throws Exception {
        Map<String, String> source = Map.ofEntries(
            Map.entry("VIETROUTE_DB_URL", "jdbc:postgresql://private-host.invalid/private_database"),
            Map.entry("VIETROUTE_DB_USER", "private_database_user"),
            Map.entry("VIETROUTE_DB_PASSWORD", "private_database_password"),
            Map.entry("VIETROUTE_DB_SCHEMA", "private_schema"),
            Map.entry("VIETROUTE_JWT_SECRET", "private-jwt-secret-012345678901234567890"),
            Map.entry("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD", "private_bootstrap_password"),
            Map.entry("VIETROUTE_MAIL_FROM", "private-sender@example.invalid"),
            Map.entry("VIETROUTE_GMAIL_CLIENT_SECRET", "private_google_secret"),
            Map.entry("VIETROUTE_GMAIL_REFRESH_TOKEN", "private_google_refresh"),
            Map.entry("VIETROUTE_SMTP_HOST", "private-mail-host.invalid"),
            Map.entry("VIETROUTE_SMTP_PASSWORD", "private_smtp_password"),
            Map.entry("VIETROUTE_CLOUDINARY_API_SECRET", "private_cloudinary_secret"),
            Map.entry("VIETROUTE_METRICS_TOKEN", "private-metrics-token-012345678901234567890"),
            Map.entry("VIETROUTE_TRUSTED_PROXY_CIDRS", "192.0.2.0/24")
        );
        var service = new SystemConfigurationService(
            new AppSettings(source::get),
            new DatabaseSettings(source::get, false),
            new TrafficSettings(source::get),
            new JobSettings(source::get),
            new MailSettings(source::get),
            new MediaSettings(source::get),
            new TrackingSettings(source::get),
            new MonitoringSettings(source::get),
            new ActivitySettings(source::get),
            Clock.systemUTC()
        );
        String json = new RootConfig().objectMapper().writeValueAsString(service.read());
        for (String secret : source.values()) {
            assertFalse(json.contains(secret), "Không được serialize giá trị cấu hình nhạy cảm");
        }
        assertFalse(json.contains("jwtKey"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("refreshToken"));
        assertTrue(json.contains("\"trackingEnabled\":true"));
        assertTrue(json.contains("\"mapStatus\":\"UNCONFIGURED\""));
    }
}
