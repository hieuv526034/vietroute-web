package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.JwtAuthenticationFilter;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.monitoring.ApiJournalSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalEntry;
import vn.vietroute.common.monitoring.filter.ApiJournalFilter;
import vn.vietroute.common.monitoring.service.ApiJournalProjection;
import vn.vietroute.common.monitoring.service.ApiJournalState;
import vn.vietroute.common.monitoring.service.ApiJournalStore;
import vn.vietroute.common.web.response.ApiErrorWriter;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.FieldViolation;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;
import vn.vietroute.support.JournalTestConfig.FakeJournalRepository;

/** Chứng minh không giữ credential/PII, kiểm quyền/service/filter thật và không queue lịch sử trong RAM; không DB production. */
class ApiJournalTest {

    @Test
    void journalUsesVerifiedActorAttributeAndKeepsItAfterLogoutPresenceIsCleared() throws Exception {
        try (var context = context()) {
            var store = context.getBean(ApiJournalStore.class);
            var filter = new ApiJournalFilter(store, context.getBean(Clock.class));
            var request = new MockHttpServletRequest("POST", Routes.AUTH_LOGOUT);
            request.addHeader("X-User-ID", "999");
            request.addHeader("X-Request-ID", "verified-actor-trace");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                incoming.setAttribute(Http.JOURNAL_USER_ATTRIBUTE, 12L);
                incoming.removeAttribute(Http.VERIFIED_USER_ATTRIBUTE);
            });
            var search = new ApiJournalSearchRequest();
            search.setTraceId("verified-actor-trace");
            assertEquals(12L, store.page(search).records().content().get(0).userId());
            var anonymous = new MockHttpServletRequest("GET", Routes.CATALOG_PREFIX + "VEHICLE_TYPE");
            anonymous.addHeader("X-User-ID", "999");
            anonymous.addHeader("X-Request-ID", "untrusted-actor-trace");
            filter.doFilter(anonymous, new MockHttpServletResponse(), (incoming, response) -> {});
            search.setTraceId("untrusted-actor-trace");
            assertNull(store.page(search).records().content().get(0).userId());
        }
    }

    @Test
    void databaseFailureDuringJwtValidationRecordsSqlStateWithoutCredentialOrController() throws Exception {
        try (var context = context()) {
            var jwt = context.getBean(JwtService.class);
            var user = UserAccount.builder().id(1L).username("private-user").roles(Set.of(Role.ADMIN)).build();
            var users = new UserRepository(null) {
                @Override
                public UserAccount findById(Long id) {
                    throw BaseRepository.translate(new SQLException("private JDBC credential", "08001"));
                }
            };
            var jwtFilter = new JwtAuthenticationFilter(
                jwt,
                users,
                context.getBean(ApiErrorWriter.class),
                context.getBean(AuthCookies.class)
            );
            var store = context.getBean(ApiJournalStore.class);
            var filter = new ApiJournalFilter(store, context.getBean(Clock.class));
            var request = new MockHttpServletRequest("GET", Routes.AUTH + Routes.ME_ACTION);
            request.addHeader("Authorization", "Bearer " + jwt.issue(user).accessToken());
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (httpRequest, httpResponse) ->
                jwtFilter.doFilter(httpRequest, httpResponse, (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("Không được đi tiếp khi xác thực DB thất bại");
                })
            );
            assertEquals(503, response.getStatus());
            var entry = store.page(new ApiJournalSearchRequest()).records().content().get(0);
            assertEquals("DB_CONNECTION", entry.diagnostic());
            assertEquals("08001", entry.sqlState());
            assertEquals("PRE_CONTROLLER", entry.controller());
            assertEquals("COM007", entry.code());
            assertFalse(
                context.getBean(ObjectMapper.class).writeValueAsString(store.find(entry.id())).contains("private")
            );
        }
    }

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
        context.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, WebConfig.class);
        context.refresh();
        return context;
    }

    @Test
    void pageSecurityAlsoRunsInSeparateServletContextLikeProductionWar() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var servletContext = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(servletContext);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            root.refresh();
            servlet.setParent(root);
            servlet.setServletContext(servletContext);
            servlet.register(WebConfig.class);
            servlet.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(servlet).apply(springSecurity()).build();
            for (String route : List.of(
                Routes.API_JOURNAL_PAGE,
                Routes.MONITORING_PAGE,
                Routes.CONFIGURATION_PAGE,
                Routes.JOBS_PAGE,
                Routes.ACCESS_PAGE
            )) {
                var denied = mvc
                    .perform(get(route).with(authentication(actor("ROLE_CUSTOMER"))))
                    .andReturn()
                    .getResponse();
                assertEquals(403, denied.getStatus(), route);
            }
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("ROLE_ADMIN"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("ROLE_ADMIN", "SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL).with(authentication(actor("ROLE_CUSTOMER"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertNull(root.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
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
    void capturesMvcValidationAndSecurityBeforeControllerWithoutSecretsOrSelfLogging() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var store = context.getBean(ApiJournalStore.class);
            var json = context.getBean(ObjectMapper.class);
            String secret = "SENTINEL_private_password_cookie_mail";
            var denied = mvc
                .perform(
                    post(Routes.AUTH_LOGIN)
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"" + secret + "\"}")
                        .header("Cookie", "private=" + secret)
                        .header("X-Request-ID", "journal-security-trace")
                )
                .andReturn()
                .getResponse();
            assertEquals(403, denied.getStatus());
            var search = new ApiJournalSearchRequest();
            search.setTraceId("journal-security-trace");
            var item = store.page(search).records().content().get(0);
            assertEquals("AUTH006", item.code());
            assertEquals("PRE_CONTROLLER", item.controller());
            assertFalse(json.writeValueAsString(store.find(item.id())).contains(secret));
            var invalid = mvc
                .perform(
                    post(Routes.ADMIN_CATALOGS)
                        .with(csrf())
                        .with(authentication(actor("ROLE_ADMIN", "CATALOG_CREATE")))
                        .contentType("application/json")
                        .content(
                            "{\"type\":\"VEHICLE_TYPE\",\"nameVi\":\"\",\"nameEn\":\"" + secret + "\",\"sortOrder\":1}"
                        )
                        .header("X-Request-ID", "journal-validation-trace")
                )
                .andReturn()
                .getResponse();
            assertEquals(400, invalid.getStatus());
            search.setTraceId("journal-validation-trace");
            item = store.page(search).records().content().get(0);
            var detail = store.find(item.id());
            assertEquals(Routes.ADMIN_CATALOGS, item.endpoint());
            assertTrue(item.controller().contains("CatalogController#create"));
            assertTrue(detail.requestBody().contains("REDACTED"));
            assertTrue(detail.requestBody().contains("VEHICLE_TYPE"));
            assertFalse(json.writeValueAsString(detail).contains(secret));
            assertEquals("COM001", item.code());
            long count = store.page(new ApiJournalSearchRequest()).records().totalElements();
            assertEquals(401, mvc.perform(get(Routes.API_JOURNAL)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL).with(authentication(actor("SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL).with(authentication(actor("ROLE_ADMIN"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var admin = actor("ROLE_ADMIN", "SYSTEM_READ");
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.API_JOURNAL).with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(get(Routes.API_JOURNAL).param("size", "101").with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                404,
                mvc
                    .perform(get(Routes.API_JOURNAL + "/" + UUID.randomUUID()).with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(count, store.page(new ApiJournalSearchRequest()).records().totalElements());
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void stripsUnmappedPathUnknownQueryAndNeverReadsMalformedOrFileBody() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var store = context.getBean(ApiJournalStore.class);
            var json = context.getBean(ObjectMapper.class);
            String secret = "SENTINEL_do_not_keep";
            mvc.perform(
                get("/api/v1/" + secret + "?password=" + secret).header("X-Request-ID", "unmapped-safety-trace")
            );
            var search = new ApiJournalSearchRequest();
            search.setTraceId("unmapped-safety-trace");
            var item = store.page(search).records().content().get(0);
            assertEquals("UNMAPPED", item.endpoint());
            assertFalse(json.writeValueAsString(store.find(item.id())).contains(secret));
            mvc.perform(
                get(Routes.CATALOGS + "/WRONG" + "?page=0&username=" + secret + "&lang=vi").header(
                    "X-Request-ID",
                    "query-safety-trace"
                )
            );
            search.setTraceId("query-safety-trace");
            var detail = store.find(store.page(search).records().content().get(0).id());
            assertEquals(Map.of("page", "0", "lang", "vi"), detail.query());
            assertFalse(json.writeValueAsString(detail).contains(secret));
            mvc.perform(
                post(Routes.ADMIN_CATALOGS)
                    .with(csrf())
                    .with(authentication(actor("CATALOG_CREATE")))
                    .contentType("application/json")
                    .content("{\"password\":\"" + secret + "\", broken")
                    .header("X-Request-ID", "malformed-safety-trace")
            );
            search.setTraceId("malformed-safety-trace");
            detail = store.find(store.page(search).records().content().get(0).id());
            assertTrue(detail.requestBody().contains("OMITTED"));
            assertFalse(json.writeValueAsString(detail).contains(secret));
        }
    }

    @Test
    void projectionIsDenyByDefaultAndBoundsResponseAndClassifiesSqlWithoutRawMessage() throws Exception {
        var mapper = new RootConfig().objectMapper();
        var projection = new ApiJournalProjection(mapper);
        var login = new LoginRequest();
        login.setUsername("secretperson");
        login.setPassword("secretpassword");
        assertFalse(projection.request(login).contains("secret"));
        assertFalse(projection.request(Map.of("future", "secret")).contains("secret"));
        var catalog = new CatalogCreateRequest();
        catalog.setNameVi("secretperson");
        catalog.setNameEn("secretpassword");
        catalog.setCode("SECRETCODE");
        assertFalse(projection.request(catalog).contains("secret"));
        assertFalse(projection.request(catalog).contains("SECRETCODE"));
        var response = new ApiResponse<>(
            false,
            "COM007",
            "error.database",
            "secret message",
            Map.of("accessToken", "secret"),
            List.of(new FieldViolation("password", "VAL001", "validation.required", "secret")),
            Instant.now(),
            "/private",
            "trace-safe",
            "vi"
        );
        String snapshot = projection.response(response, Routes.AUTH_LOGIN);
        assertFalse(snapshot.contains("secret"));
        assertTrue(snapshot.length() < ApiJournalProjection.MAX_SNAPSHOT);
        var request = new MockHttpServletRequest();
        var state = ApiJournalState.start(request);
        var exception = BaseRepository.translate(new SQLException("private SQL password JDBC", "42P01"));
        ApiJournalState.failure(request, exception);
        assertEquals("42P01", state.sqlState);
        assertEquals("DB_SCHEMA_OR_QUERY", state.diagnostic);
        assertEquals(ErrorCode.DATABASE_UNAVAILABLE, exception.getErrorCode());
        // Pool có thể bọc SQLException không SQLState ngoài lỗi driver có SQLState ở bên trong.
        ApiJournalState.start(request);
        ApiJournalState.failure(request, new SQLException("private pool message", new SQLException("driver", "08001")));
        assertEquals("DB_CONNECTION", ApiJournalState.get(request).diagnostic);
        assertEquals("08001", ApiJournalState.get(request).sqlState);
    }

    private ApiJournalDetail detail(Instant timestamp) {
        var e = new ApiJournalEntry(
            UUID.randomUUID(),
            timestamp,
            "trace-safe",
            "GET",
            Routes.AUTH + "/me",
            "AuthController#me",
            200,
            10,
            "OK000",
            "success.ok",
            "SUCCESS",
            "",
            null,
            null
        );
        return new ApiJournalDetail(e, "test-node", Routes.AUTH + "/me", Map.of(), "{}", "{}", "JSON");
    }

    @Test
    void busyWriterHasNoHistoryQueueAndFailureBackoffDoesNotRetryEveryRequest() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var repository = new FakeJournalRepository() {
            @Override
            public void append(ApiJournalDetail detail) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                super.append(detail);
            }
        };
        var settings = new ApiJournalSettings(Map.<String, String>of()::get);
        var store = new ApiJournalStore(settings, repository);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(() -> store.add(detail(Instant.now())));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            store.add(detail(Instant.now())); // Slot bận: không cache/queue lại snapshot.
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            assertEquals(1, store.page(new ApiJournalSearchRequest()).records().totalElements());
            assertEquals(1, store.page(new ApiJournalSearchRequest()).failedWrites());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        var attempts = new AtomicInteger();
        var unavailable = new FakeJournalRepository() {
            @Override
            public void append(ApiJournalDetail detail) {
                attempts.incrementAndGet();
                throw new IllegalStateException("DB offline");
            }
        };
        var failedStore = new ApiJournalStore(settings, unavailable);
        for (int i = 0; i < 20; i++) failedStore.add(detail(Instant.now()));
        assertEquals(1, attempts.get());
        assertEquals(20, failedStore.page(new ApiJournalSearchRequest()).failedWrites());
        assertEquals(0, failedStore.page(new ApiJournalSearchRequest()).records().totalElements());
        var disabled = new ApiJournalStore(
            new ApiJournalSettings(Map.of("VIETROUTE_API_JOURNAL_ENABLED", "false")::get),
            unavailable
        );
        disabled.add(detail(Instant.now()));
        assertEquals(1, attempts.get());
    }

    @Test
    void protectedPageAndOpenApiDocumentBothLocalesWithoutDatabase() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            assertEquals(302, mvc.perform(get(Routes.API_JOURNAL_PAGE)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (String locale : List.of("vi", "en")) {
                var result = mvc
                    .perform(
                        get(Routes.API_JOURNAL_PAGE)
                            .param("lang", locale)
                            .with(authentication(actor("ROLE_ADMIN", "SYSTEM_READ")))
                    )
                    .andReturn()
                    .getResponse();
                assertEquals(200, result.getStatus());
                assertTrue(result.getContentAsString().contains("journal-dialog"));
                assertFalse(result.getContentAsString().contains("??ui.journal"));
            }
            String api = mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse().getContentAsString();
            assertTrue(api.contains(Routes.API_JOURNAL));
            assertTrue(api.contains("ApiJournalDetail"));
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }
}
