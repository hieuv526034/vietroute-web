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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.JwtAuthenticationFilter;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.catalog.dto.request.CategoryRequest;
import vn.vietroute.common.api_journal.dto.response.ApiJournalDetail;
import vn.vietroute.common.api_journal.dto.response.ApiJournalEntry;
import vn.vietroute.common.api_journal.dto.response.ApiJournalResponse;
import vn.vietroute.common.api_journal.dto.search.ApiJournalSearch;
import vn.vietroute.common.api_journal.filter.ApiJournalFilter;
import vn.vietroute.common.api_journal.filter.ApiJournalFilter.ApiJournalState;
import vn.vietroute.common.api_journal.service.ApiJournalService;
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.FieldViolation;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;
import vn.vietroute.support.JournalTestConfig.FakeJournalRepository;
import vn.vietroute.support.TestJpa;

/** Chứng minh không giữ credential/PII, kiểm quyền/service/filter thật và không queue lịch sử trong RAM; không DB production. */
class ApiJournalTest {

    @Test
    void journalUsesVerifiedActorAttributeAndKeepsItAfterLogoutPresenceIsCleared() throws Exception {
        try (var context = context()) {
            var store = context.getBean(ApiJournalService.class);
            var filter = new ApiJournalFilter(store, context.getBean(Clock.class));
            var request = new MockHttpServletRequest("POST", Routes.AUTH_LOGOUT);
            request.addHeader("X-User-ID", "999");
            request.addHeader("X-Request-ID", "verified-actor-trace");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                incoming.setAttribute(Http.JOURNAL_USER_ATTRIBUTE, 12L);
                incoming.removeAttribute(Http.VERIFIED_USER_ATTRIBUTE);
            });
            var search = new ApiJournalSearch();
            search.setTraceId("verified-actor-trace");
            assertEquals(12L, page(store, search).getRecords().getContent().get(0).getUserId());
            var anonymous = new MockHttpServletRequest("GET", Routes.CATEGORIES);
            anonymous.addHeader("X-User-ID", "999");
            anonymous.addHeader("X-Request-ID", "untrusted-actor-trace");
            filter.doFilter(anonymous, new MockHttpServletResponse(), (incoming, response) -> {});
            search.setTraceId("untrusted-actor-trace");
            assertNull(page(store, search).getRecords().getContent().get(0).getUserId());
        }
    }

    @Test
    void databaseFailureDuringJwtValidationRecordsSqlStateWithoutCredentialOrController() throws Exception {
        try (var context = context()) {
            var jwt = context.getBean(JwtService.class);
            var user = User.builder().id(1L).username("private-user").roles(Set.of("ADMIN")).build();
            var users = TestJpa.stub(UserRepo.class, (method, args) -> {
                throw BaseRepository.translate(new SQLException("private JDBC credential", "08001"));
            });
            var jwtFilter = new JwtAuthenticationFilter(
                jwt,
                users,
                context.getBean(ApiResponses.class),
                context.getBean(AuthCookies.class)
            );
            var store = context.getBean(ApiJournalService.class);
            var filter = new ApiJournalFilter(store, context.getBean(Clock.class));
            var request = new MockHttpServletRequest("GET", Routes.AUTH + Routes.ME_ACTION);
            request.addHeader("Authorization", "Bearer " + jwt.issue(user).getAccessToken());
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (httpRequest, httpResponse) ->
                jwtFilter.doFilter(httpRequest, httpResponse, (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("Không được đi tiếp khi xác thực DB thất bại");
                })
            );
            assertEquals(503, response.getStatus());
            var entry = page(store, new ApiJournalSearch()).getRecords().getContent().get(0);
            assertEquals("DB_CONNECTION", entry.getDiagnostic());
            assertEquals("08001", entry.getSqlState());
            assertEquals("PRE_CONTROLLER", entry.getController());
            assertEquals("COM007", entry.getCode());
            assertFalse(
                context.getBean(ObjectMapper.class).writeValueAsString(find(store, entry.getId())).contains("private")
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
            for (String route : List.of(Routes.API_JOURNAL_PAGE, Routes.ACCESS_PAGE)) {
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
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("ROLE_ADMIN"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(
                        get(Routes.API_JOURNAL_PAGE).with(
                            authentication(
                                actor(
                                    "GET " + Routes.API_JOURNAL_PAGE,
                                    "GET " + Routes.API_JOURNAL,
                                    "GET " + Routes.API_JOURNAL + "/{id}"
                                )
                            )
                        )
                    )
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
                Set.of(List.of(authorities).contains("ROLE_ADMIN") ? "ADMIN" : "OPERATOR"),
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
            var store = context.getBean(ApiJournalService.class);
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
            var search = new ApiJournalSearch();
            search.setTraceId("journal-security-trace");
            var item = page(store, search).getRecords().getContent().get(0);
            assertEquals("AUTH006", item.getCode());
            assertEquals("PRE_CONTROLLER", item.getController());
            assertFalse(json.writeValueAsString(find(store, item.getId())).contains(secret));
            var invalid = mvc
                .perform(
                    post(Routes.ADMIN_CATEGORIES)
                        .with(csrf())
                        .with(authentication(actor("ROLE_ADMIN", "POST " + Routes.ADMIN_CATEGORIES)))
                        .contentType("application/json")
                        .content(
                            "{\"type\":\"VALUE\",\"refType\":\"VEHICLE_TYPE\",\"name\":\"\",\"props\":{\"nameEn\":\"" +
                                secret +
                                "\"},\"sortOrder\":1}"
                        )
                        .header("X-Request-ID", "journal-validation-trace")
                )
                .andReturn()
                .getResponse();
            assertEquals(400, invalid.getStatus());
            search.setTraceId("journal-validation-trace");
            item = page(store, search).getRecords().getContent().get(0);
            var detail = find(store, item.getId());
            assertEquals(Routes.ADMIN_CATEGORIES, item.getEndpoint());
            assertTrue(item.getController().contains("CategoryAPI#create"));
            assertTrue(detail.getRequestBody().contains("REDACTED"));
            assertTrue(detail.getRequestBody().contains("VALUE"));
            assertFalse(detail.getRequestBody().contains("props"));
            assertFalse(json.writeValueAsString(detail).contains(secret));
            assertEquals("COM001", item.getCode());
            long count = page(store, new ApiJournalSearch()).getRecords().getTotalElements();
            assertEquals(401, mvc.perform(get(Routes.API_JOURNAL)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.API_JOURNAL).with(authentication(actor("ROLE_ADMIN"))))
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
            var admin = actor(
                "GET " + Routes.API_JOURNAL_PAGE,
                "GET " + Routes.API_JOURNAL,
                "GET " + Routes.API_JOURNAL + "/{id}"
            );
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
            assertEquals(count, page(store, new ApiJournalSearch()).getRecords().getTotalElements());
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void stripsUnmappedPathUnknownQueryAndNeverReadsMalformedOrFileBody() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var store = context.getBean(ApiJournalService.class);
            var json = context.getBean(ObjectMapper.class);
            String secret = "SENTINEL_do_not_keep";
            mvc.perform(
                get("/api/v1/" + secret + "?password=" + secret).header("X-Request-ID", "unmapped-safety-trace")
            );
            var search = new ApiJournalSearch();
            search.setTraceId("unmapped-safety-trace");
            var item = page(store, search).getRecords().getContent().get(0);
            assertEquals("UNMAPPED", item.getEndpoint());
            assertFalse(json.writeValueAsString(find(store, item.getId())).contains(secret));
            mvc.perform(
                get(Routes.CATEGORIES + "/WRONG" + "?page=0&username=" + secret + "&lang=vi").header(
                    "X-Request-ID",
                    "query-safety-trace"
                )
            );
            search.setTraceId("query-safety-trace");
            var detail = find(store, page(store, search).getRecords().getContent().get(0).getId());
            assertEquals(Map.of("page", "0", "lang", "vi"), detail.getQuery());
            assertFalse(json.writeValueAsString(detail).contains(secret));
            mvc.perform(
                post(Routes.ADMIN_CATEGORIES)
                    .with(csrf())
                    .with(authentication(actor("CATALOG_CREATE")))
                    .contentType("application/json")
                    .content("{\"password\":\"" + secret + "\", broken")
                    .header("X-Request-ID", "malformed-safety-trace")
            );
            search.setTraceId("malformed-safety-trace");
            detail = find(store, page(store, search).getRecords().getContent().get(0).getId());
            assertTrue(detail.getRequestBody().contains("OMITTED"));
            assertFalse(json.writeValueAsString(detail).contains(secret));
        }
    }

    @Test
    void projectionIsDenyByDefaultAndBoundsResponseAndClassifiesSqlWithoutRawMessage() throws Exception {
        var mapper = new RootConfig().objectMapper();
        var projection = new ApiJournalService(new AppSettings(key -> null), null, mapper);
        var login = new UserRequest();
        login.setUsername("secretperson");
        login.setPassword("secretpassword");
        assertFalse(projection.request(login).contains("secret"));
        assertFalse(projection.request(Map.of("future", "secret")).contains("secret"));
        var catalog = new CategoryRequest();
        catalog.setName("secretperson");
        catalog.setProps(Map.of("nameEn", "secretpassword"));
        catalog.setCode("SECRETCODE");
        assertFalse(projection.request(catalog).contains("secret"));
        assertFalse(projection.request(catalog).contains("SECRETCODE"));
        assertFalse(mapper.readTree(projection.request(catalog)).has("active"));
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
        assertTrue(snapshot.length() < ApiJournalService.MAX_SNAPSHOT);
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

    @Test
    void businessPayloadKeepsOperationalFieldsAndOmitsContactData() throws Exception {
        ObjectMapper mapper = new RootConfig().objectMapper();
        ApiJournalService journal = new ApiJournalService(new AppSettings(key -> null), null, mapper);
        BookingRequest booking = new BookingRequest();
        booking.setTripId(12);
        booking.setSeats(2);
        booking.setContactName("private-person");
        booking.setContactPhone("0900000011");
        String snapshot = journal.request(booking);
        assertEquals(12, mapper.readTree(snapshot).path("tripId").asLong());
        assertEquals(2, mapper.readTree(snapshot).path("seats").asInt());
        assertFalse(snapshot.contains("private-person"));
        assertFalse(snapshot.contains("0900000011"));
        assertFalse(snapshot.contains("contactPhone"));
    }

    @Test
    void directJournalServiceCannotBorrowAnApprovedBusinessTarget() {
        ApiJournalService journal = new ApiJournalService(new AppSettings(key -> null), null, new ObjectMapper());
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            approveJournal("GET /api/v1/admin/carriers");
            assertThrows(BusinessException.class, () -> journal.page(new ApiJournalSearch()));
            assertThrows(BusinessException.class, () -> journal.find(UUID.randomUUID()));
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private ApiJournalDetail detail(Instant timestamp) {
        var e = new ApiJournalEntry(
            UUID.randomUUID(),
            timestamp,
            "trace-safe",
            "GET",
            Routes.AUTH + "/me",
            "UserSessionAPI#me",
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
        var settings = new AppSettings(Map.<String, String>of()::get);
        var store = new ApiJournalService(settings, repository, new ObjectMapper());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(() -> store.add(detail(Instant.now())));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            store.add(detail(Instant.now())); // Slot bận: không cache/queue lại snapshot.
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            assertEquals(1, page(store, new ApiJournalSearch()).getRecords().getTotalElements());
            assertEquals(1, page(store, new ApiJournalSearch()).getFailedWrites());
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
        var failedStore = new ApiJournalService(settings, unavailable, new ObjectMapper());
        for (int i = 0; i < 20; i++) failedStore.add(detail(Instant.now()));
        assertEquals(1, attempts.get());
        assertEquals(20, page(failedStore, new ApiJournalSearch()).getFailedWrites());
        assertEquals(0, page(failedStore, new ApiJournalSearch()).getRecords().getTotalElements());
        var disabled = new ApiJournalService(
            new AppSettings(Map.of("VIETROUTE_API_JOURNAL_ENABLED", "false")::get),
            unavailable,
            new ObjectMapper()
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
                    .perform(get(Routes.API_JOURNAL_PAGE).with(authentication(actor("ROLE_ADMIN"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (String locale : List.of("vi", "en")) {
                var result = mvc
                    .perform(
                        get(Routes.API_JOURNAL_PAGE)
                            .param("lang", locale)
                            .with(
                                authentication(
                                    actor(
                                        "GET " + Routes.API_JOURNAL_PAGE,
                                        "GET " + Routes.API_JOURNAL,
                                        "GET " + Routes.API_JOURNAL + "/{id}"
                                    )
                                )
                            )
                    )
                    .andReturn()
                    .getResponse();
                assertEquals(200, result.getStatus());
                assertTrue(result.getContentAsString().contains("journal-dialog"));
                assertFalse(result.getContentAsString().contains("??ui.journal"));
            }
            var document = mvc
                .perform(get(Routes.OPENAPI).with(authentication(actor("GET " + Routes.OPENAPI))))
                .andReturn()
                .getResponse();
            assertEquals(200, document.getStatus());
            String api = document.getContentAsString();
            assertTrue(api.contains(Routes.API_JOURNAL));
            assertTrue(api.contains("ApiJournalDetail"));
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    private ApiJournalResponse page(ApiJournalService store, ApiJournalSearch search) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            approveJournal("GET " + Routes.API_JOURNAL);
            return store.page(search);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private ApiJournalDetail find(ApiJournalService store, UUID id) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            approveJournal("GET " + Routes.API_JOURNAL_DETAIL);
            return store.find(id);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private void approveJournal(String target) {
        User user = User.builder().id(1L).username("journal-reader").roles(Set.of("CUSTOM_READER")).build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(user, target), null, List.of())
        );
    }
}
