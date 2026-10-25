package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.service.VehicleService;
import vn.vietroute.business.catalog.service.BookingSourceCatalogService;
import vn.vietroute.business.report.dto.request.ReportSearchRequest;
import vn.vietroute.business.report.service.ReportService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.media.service.MediaService;

/** PostgreSQL thật, hai context WAR; chỉ cho phép DB test loopback, không đọc .env hoặc dùng Neon. */
@EnabledIfSystemProperty(
    named = "business.test.database",
    matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/vietroute_business_test"
)
class BusinessIntegrationTest {

    private static String schema;
    private AnnotationConfigWebApplicationContext root;
    private AnnotationConfigWebApplicationContext child;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private Authentication admin;

    @Configuration
    static class Environment {

        @Bean
        @Primary
        ConfigurationEnvironment businessTestEnvironment() {
            var values = Map.ofEntries(
                Map.entry("VIETROUTE_ENV", "development"),
                Map.entry("VIETROUTE_COOKIE_SECURE", "false"),
                Map.entry("VIETROUTE_DB_URL", System.getProperty("business.test.database")),
                Map.entry("VIETROUTE_DB_USER", System.getProperty("business.test.user", "ciadmin")),
                Map.entry("VIETROUTE_DB_PASSWORD", "test_only"),
                Map.entry("VIETROUTE_DB_SCHEMA", schema),
                Map.entry("VIETROUTE_DB_INIT_ENABLED", "true"),
                Map.entry("VIETROUTE_ACCESS_SCOPE_CAPACITY", "2"),
                Map.entry("VIETROUTE_ACCESS_SYNC_ENABLED", "true"),
                Map.entry("VIETROUTE_JWT_SECRET", "business_test_only_never_use_for_runtime_0123456789"),
                Map.entry("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME", "testadmin"),
                Map.entry("VIETROUTE_BOOTSTRAP_ADMIN_EMAIL", "testadmin@example.invalid"),
                Map.entry("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD", "TestOnly!2026Aa"),
                Map.entry("VIETROUTE_AUTH_SELF_SERVICE_ENABLED", "false"),
                Map.entry("VIETROUTE_WORKER_ENABLED", "false"),
                Map.entry("VIETROUTE_MAIL_ENABLED", "false"),
                Map.entry("VIETROUTE_MEDIA_ENABLED", "false"),
                Map.entry("VIETROUTE_API_JOURNAL_ENABLED", "false"),
                Map.entry("VIETROUTE_ACTIVITY_ENABLED", "false"),
                Map.entry("VIETROUTE_MONITORING_DB_ENABLED", "false")
            );
            return new ConfigurationEnvironment(values::get, null);
        }
    }

    @BeforeEach
    void start() {
        schema = "business02_it_" + UUID.randomUUID().toString().replace("-", "");
        var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
        root = new AnnotationConfigWebApplicationContext();
        root.setServletContext(web);
        root.register(RootConfig.class, Environment.class);
        root.refresh();
        child = new AnnotationConfigWebApplicationContext();
        child.setParent(root);
        child.setServletContext(web);
        child.register(WebConfig.class);
        child.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
        admin = actor(1, "testadmin", "ADMIN");
    }

    @AfterEach
    void stop() throws Exception {
        SecurityContextHolder.clearContext();
        if (child != null) child.close();
        if (root != null) root.close();
        // Schema random do test tạo; URL/class condition chặn tuyệt đối target ngoài loopback test.
        if (schema != null && schema.matches("business02_it_[a-f0-9]{32}")) {
            try (
                var connection = DriverManager.getConnection(
                    System.getProperty("business.test.database"),
                    System.getProperty("business.test.user", "ciadmin"),
                    "test_only"
                );
                var statement = connection.createStatement()
            ) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private Authentication actor(long id, String username, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(id, username, Set.of(Role.valueOf(role)), 0),
            null,
            Set.of()
        );
    }

    private JsonNode call(int expected, String method, String path, String body, Authentication who) throws Exception {
        var request = request(HttpMethod.valueOf(method), path).with(authentication(who)).with(csrf());
        if (body != null) request.contentType("application/json").content(body);
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(expected, response.getStatus(), method + " " + path + " (chỉ in mã lỗi khi thất bại)");
        return response.getContentType() != null && response.getContentType().contains("json")
            ? json.readTree(response.getContentAsString()).path("data")
            : json.nullNode();
    }

    private String sql(String text) throws Exception {
        try (
            var connection = DriverManager.getConnection(
                System.getProperty("business.test.database"),
                System.getProperty("business.test.user", "ciadmin"),
                "test_only"
            );
            var statement = connection.createStatement()
        ) {
            statement.execute("SET search_path TO " + schema + ",pg_temp");
            if (!statement.execute(text)) return "";
            try (var rows = statement.getResultSet()) {
                return rows.next() ? rows.getString(1) : "";
            }
        }
    }

    private JsonNode seed() throws Exception {
        return call(200, "POST", "/api/v1/admin/demo", "{}", admin);
    }

    private long id(String table, String code) throws Exception {
        return Long.parseLong(sql("SELECT id FROM " + table + " WHERE code='" + code + "'"));
    }

    private Authentication member(long scope, String role, String permissions, String username) throws Exception {
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES ('" +
                role +
                "','Kiểm thử','Test'); INSERT INTO auth_role_permissions(role_code,permission_code) SELECT '" +
                role +
                "',code FROM auth_permissions WHERE code IN (" +
                permissions +
                ")"
        );
        long user = Long.parseLong(
            sql(
                "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email,email_verified_at) SELECT '" +
                    username +
                    "',password_hash,'Test',false,'" +
                    username +
                    "@example.invalid',CURRENT_TIMESTAMP FROM auth_users WHERE id=1 RETURNING id"
            )
        );
        sql(
            "INSERT INTO auth_user_roles(user_id,role_code) VALUES (" +
                user +
                ",'" +
                role +
                "'); INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES (" +
                scope +
                "," +
                user +
                "); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES (" +
                scope +
                "," +
                user +
                ",'" +
                role +
                "')"
        );
        return actor(user, username, role);
    }

    @Test
    void reportUsesDepartureCohortCountsOnceAndKeepsCancelledMoneyVisible() throws Exception {
        seed();
        var own = tripFixture(false);
        var other = tripFixture(true);
        var day = Instant.now().atZone(ZoneId.of(Business.TIME_ZONE)).toLocalDate().plusDays(2);
        long trip = openBookingTrip(own, "REPORTA", day.atStartOfDay(ZoneId.of(Business.TIME_ZONE)).toInstant());
        long foreign = openBookingTrip(
            other,
            "REPORTB",
            day.atTime(23, 59).atZone(ZoneId.of(Business.TIME_ZONE)).toInstant()
        );
        call(
            201,
            "POST",
            "/api/v1/admin/trips",
            tripBody(own, "REPORTOUTSIDE", day.plusDays(7).atStartOfDay(ZoneId.of(Business.TIME_ZONE)).toInstant()),
            admin
        );
        call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 3, UUID.randomUUID()), admin);
        for (long tripId : new long[] { trip, foreign }) {
            long booking = call(
                201,
                "POST",
                "/api/v1/admin/bookings",
                bookingBody(tripId, 1, 3, tripId == trip ? 2 : 3, UUID.randomUUID()),
                admin
            )
                .path("id")
                .asLong();
            call(
                200,
                "PUT",
                "/api/v1/admin/bookings/" + booking + "/state",
                "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
                admin
            );
        }
        long cancelled = call(
            201,
            "POST",
            "/api/v1/admin/bookings",
            bookingBody(trip, 1, 2, 1, UUID.randomUUID()),
            admin
        )
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + cancelled + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + cancelled + "/payment",
            "{\"kind\":\"PAYMENT\",\"amount\":100000,\"note\":\"Thu TEST\",\"version\":1,\"requestKey\":\"" +
                UUID.randomUUID() +
                "\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + cancelled + "/state",
            "{\"status\":\"CANCELLED\",\"version\":2,\"note\":\"Hủy TEST\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + cancelled + "/payment",
            "{\"kind\":\"REFUND\",\"amount\":40000,\"note\":\"Hoàn TEST\",\"version\":3,\"requestKey\":\"" +
                UUID.randomUUID() +
                "\"}",
            admin
        );
        String path = "/api/v1/admin/reports?fromDate=" + day + "&toDate=" + day;
        var global = call(200, "GET", path, null, admin);
        assertEquals(2, global.path("tripCount").asInt());
        assertEquals(4, global.path("bookingCount").asInt());
        assertEquals(5, global.path("confirmedSeats").asInt());
        assertEquals(1250000, global.path("confirmedValue").asInt());
        assertEquals(100000, global.path("paidAmount").asInt());
        assertEquals(40000, global.path("refundedAmount").asInt());
        assertEquals(60000, global.path("netAmount").asInt());
        assertEquals(60000, global.path("refundOutstanding").asInt());
        assertEquals(5, global.path("trips").size());
        assertEquals(5, global.path("bookings").size());
        assertFalse(global.has("contactName"));
        var scoped = call(200, "GET", path + "&carrierId=" + own.carrier(), null, admin);
        assertEquals(1, scoped.path("tripCount").asInt());
        assertEquals(3, scoped.path("bookingCount").asInt());
        assertEquals(2, scoped.path("confirmedSeats").asInt());
        assertEquals(500000, scoped.path("confirmedValue").asInt());
        var empty = call(
            200,
            "GET",
            "/api/v1/admin/reports?fromDate=" + day.minusDays(1) + "&toDate=" + day.minusDays(1),
            null,
            admin
        );
        assertEquals(0, empty.path("tripCount").asInt());
        assertEquals(0, empty.path("netAmount").asInt());
        sql("UPDATE carriers SET active=false WHERE id=" + own.carrier());
        assertEquals(
            3,
            call(200, "GET", path + "&carrierId=" + own.carrier(), null, admin)
                .path("bookingCount")
                .asInt()
        );
    }

    @Test
    void reportReadAndViewAreIndependentAndScopedRightsCannotBeUnionedOrReusedAfterRevocation() throws Exception {
        seed();
        var own = tripFixture(false);
        var other = tripFixture(true);
        openBookingTrip(own, "REPORTSCOPEA", Instant.now().plus(1, ChronoUnit.DAYS));
        openBookingTrip(other, "REPORTSCOPEB", Instant.now().plus(1, ChronoUnit.DAYS));
        var reporter = member(own.scope(), "REPORTER", "'REPORT_READ'", "reporter");
        long user = ((UserPrincipal) reporter.getPrincipal()).id();
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES('REPORTEMPTY','Rỗng','Empty'); INSERT INTO auth_user_roles(user_id,role_code) VALUES(" +
                user +
                ",'REPORTEMPTY'); INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES(" +
                other.scope() +
                "," +
                user +
                "); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                other.scope() +
                "," +
                user +
                ",'REPORTEMPTY')"
        );
        var from = Instant.now().atZone(ZoneId.of(Business.TIME_ZONE)).toLocalDate();
        String path = "/api/v1/admin/reports?fromDate=" + from + "&toDate=" + from.plusDays(5);
        assertEquals(1, call(200, "GET", path, null, reporter).path("tripCount").asInt());
        var choices = call(200, "GET", "/api/v1/admin/reports/carriers?size=1", null, reporter);
        assertEquals(1, choices.path("totalElements").asInt());
        assertEquals(own.carrier(), choices.path("content").get(0).path("id").asLong());
        assertFalse(choices.path("content").get(0).has("scopeId"));
        call(404, "GET", path + "&carrierId=" + other.carrier(), null, reporter);
        call(403, "GET", "/operations/reports", null, reporter);
        call(403, "GET", path, null, customer("reportbuyer"));
        sql("INSERT INTO auth_role_permissions(role_code,permission_code) VALUES('REPORTER','REPORT_VIEW')");
        call(200, "GET", "/operations/reports?lang=vi", null, reporter);
        call(200, "GET", "/operations/reports?lang=en", null, reporter);
        sql("UPDATE auth_scope_memberships SET active=false WHERE scope_id=" + own.scope() + " AND user_id=" + user);
        assertEquals(0, call(200, "GET", path, null, reporter).path("tripCount").asInt());
        call(404, "GET", path + "&carrierId=" + own.carrier(), null, reporter);
        sql("DELETE FROM auth_role_permissions WHERE role_code='REPORTER' AND permission_code='REPORT_READ'");
        call(403, "GET", path, null, reporter);
        SecurityContextHolder.getContext().setAuthentication(reporter);
        assertThrows(AccessDeniedException.class, () ->
            root.getBean(ReportService.class).summary(new ReportSearchRequest())
        );
        call(200, "GET", "/operations/reports", null, reporter);
    }

    @Test
    void reportRejectsUnboundedOrReversedDatesAndDocumentsEveryQueryInput() throws Exception {
        for (String query : new String[] {
            "fromDate=2026-10-25&toDate=2026-10-24",
            "fromDate=2025-01-01&toDate=2026-01-02",
            "fromDate=&toDate=2026-10-25",
            "fromDate=wrong",
            "carrierId=0",
        }) {
            call(400, "GET", "/api/v1/admin/reports?" + query, null, admin);
        }
        var response = mvc
            .perform(
                request(HttpMethod.GET, "/api/v1/admin/reports?fromDate=2026-10-25&toDate=2026-10-24").with(
                    authentication(admin)
                )
            )
            .andReturn()
            .getResponse();
        assertTrue(response.getContentAsString().contains("VAL016"));
        call(200, "GET", "/api/v1/admin/reports?fromDate=2024-01-01&toDate=2024-12-31", null, admin);
        // OpenAPI không dùng wrapper ApiResponse; đọc response gốc để đối chiếu tham số thật.
        var raw = mvc
            .perform(request(HttpMethod.GET, "/v3/api-docs").with(authentication(admin)))
            .andReturn()
            .getResponse();
        var contract = json.readTree(raw.getContentAsString());
        var parameters = contract.path("paths").path("/api/v1/admin/reports").path("get").path("parameters");
        assertEquals(5, parameters.size());
        for (String name : new String[] { "fromDate", "toDate", "carrierId" })
            assertTrue(parameters.toString().contains("\"" + name + "\""));
        var picker = contract.path("paths").path("/api/v1/admin/reports/carriers").path("get").path("parameters");
        assertTrue(picker.toString().contains("\"page\""));
        assertTrue(picker.toString().contains("\"size\""));
        assertTrue(picker.toString().contains("\"query\""));
    }

    @Test
    void demoIsAtomicIdempotentAndCatalogTypesCannotCrossIds() throws Exception {
        assertEquals(21, seed().path("created").asInt());
        var repeat = seed();
        assertEquals(0, repeat.path("created").asInt());
        assertEquals(21, repeat.path("reused").asInt());
        assertEquals("2", sql("SELECT count(*) FROM auth_data_scopes"));
        assertEquals("2", sql("SELECT count(*) FROM auth_users"));
        long source = id("catalog_items", "DEMOWEB");
        call(404, "GET", "/api/v1/admin/vehicle-types/" + source, null, admin);
        call(
            404,
            "PUT",
            "/api/v1/admin/vehicle-types/" + source,
            "{\"nameVi\":\"Sai\",\"nameEn\":\"Wrong\",\"sortOrder\":0,\"version\":0,\"active\":true}",
            admin
        );
        call(404, "DELETE", "/api/v1/admin/vehicle-types/" + source, "{\"version\":0}", admin);
        assertEquals(
            3,
            call(200, "GET", "/api/v1/catalogs/VEHICLE_TYPE?query=DEMO&size=2", null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(200, "GET", "/api/v1/catalogs/VEHICLE_TYPE?query=%25_%27", null, admin).path("totalElements").asInt()
        );
    }

    @Test
    void membershipFiltersCountRowsIdsAndPickerWithoutLeakingOtherCarrier() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var operator = member(
            scope,
            "TESTOP",
            "'VEHICLE_VIEW','VEHICLE_READ','VEHICLE_CREATE','VEHICLE_UPDATE'",
            "testoperator"
        );
        var page = call(200, "GET", "/api/v1/admin/vehicles?size=1", null, operator);
        assertEquals(2, page.path("totalElements").asInt());
        assertEquals(1, page.path("content").size());
        assertTrue(page.path("content").get(0).path("canUpdate").asBoolean());
        assertEquals(
            0,
            call(200, "GET", "/api/v1/admin/vehicles?carrierId=" + id("carriers", "DEMOBACNAM"), null, operator)
                .path("totalElements")
                .asInt()
        );
        var picker = call(200, "GET", "/api/v1/admin/vehicles/carriers", null, operator);
        assertEquals(1, picker.path("totalElements").asInt());
        assertFalse(picker.toString().contains("phone"));
        long other = id("carrier_vehicles", "DEMOVEH03");
        call(404, "GET", "/api/v1/admin/vehicles/" + other, null, operator);
        long type = id("catalog_items", "DEMOSEAT");
        String body =
            "{\"plateNumber\":\"DEMO-999\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":20,\"active\":true,\"version\":0}";
        call(404, "PUT", "/api/v1/admin/vehicles/" + other, body, operator);
        call(403, "GET", "/api/v1/admin/carriers", null, operator);
        call(403, "GET", "/catalogs/booking-sources", null, operator);
        call(403, "POST", "/api/v1/admin/demo", "{}", operator);
        // UPDATE toàn cục từ role khác không được union sang role scoped chỉ còn READ.
        long operatorId = ((UserPrincipal) operator.getPrincipal()).id();
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES ('GLOBALWRITE','Test','Test'); INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('GLOBALWRITE','VEHICLE_UPDATE'); INSERT INTO auth_user_roles(user_id,role_code) VALUES (" +
                operatorId +
                ",'GLOBALWRITE'); DELETE FROM auth_role_permissions WHERE role_code='TESTOP' AND permission_code='VEHICLE_UPDATE'"
        );
        var scopedReadOnly = call(200, "GET", "/api/v1/admin/vehicles", null, operator);
        assertFalse(scopedReadOnly.path("content").get(0).path("canUpdate").asBoolean());
        call(404, "PUT", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "DEMOVEH01"), body, operator);
        // Thu hồi membership trong DB có hiệu lực ở lần kiểm tiếp theo dù principal vẫn như cũ.
        sql("UPDATE auth_scope_memberships SET active=false WHERE scope_id=" + scope);
        assertEquals(0, call(200, "GET", "/api/v1/admin/vehicles", null, operator).path("totalElements").asInt());
    }

    @Test
    void viewReadAndTypePermissionsStayIndependentInBothContexts() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var view = member(scope, "VIEWONLY", "'VEHICLE_VIEW','VEHICLE_TYPE_VIEW'", "testviewer");
        call(200, "GET", "/operations/vehicles", null, view);
        call(200, "GET", "/catalogs/vehicle-types", null, view);
        call(403, "GET", "/api/v1/admin/vehicles", null, view);
        call(403, "GET", "/catalogs/booking-sources", null, view);
        SecurityContextHolder.getContext().setAuthentication(view);
        assertThrows(AccessDeniedException.class, () ->
            root.getBean(VehicleService.class).page(new CarrierSearchRequest())
        );
        assertThrows(AccessDeniedException.class, () ->
            root.getBean(BookingSourceCatalogService.class).find(1, Locale.ROOT)
        );
        var read = member(scope, "TYPEONLY", "'VEHICLE_TYPE_READ'", "testtypereader");
        call(200, "GET", "/api/v1/admin/vehicle-types", null, read);
        call(403, "GET", "/catalogs/vehicle-types", null, read);
        call(403, "GET", "/api/v1/admin/booking-sources", null, read);
        call(403, "POST", "/api/v1/admin/catalogs", "{}", read);
    }

    @Test
    void plateUniquenessParentStatusVersionsAndDriverAccountAreEnforced() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM");
        long vehicle = id("carrier_vehicles", "DEMOVEH01");
        long type = id("catalog_items", "DEMOSEAT");
        String update =
            "{\"plateNumber\":\"DEMO-001\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":28,\"active\":true,\"version\":0}";
        call(200, "PUT", "/api/v1/admin/vehicles/" + vehicle, update, admin);
        call(409, "PUT", "/api/v1/admin/vehicles/" + vehicle, update, admin);
        String create =
            "{\"code\":\"TESTDUP\",\"carrierId\":" +
            carrier +
            ",\"plateNumber\":\"DEMO.001\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":20,\"active\":true}";
        call(409, "POST", "/api/v1/admin/vehicles", create, admin);
        call(
            400,
            "POST",
            "/api/v1/admin/vehicles",
            create.replace("TESTDUP", "TESTINVALID").replace("DEMO.001", "TEST-005").replace("20,", "201,"),
            admin
        );
        call(
            400,
            "POST",
            "/api/v1/admin/drivers",
            "{\"code\":\"TESTDRIVER\",\"carrierId\":" +
                carrier +
                ",\"name\":\"Test\",\"phone\":\"0000000000\",\"userId\":1}",
            admin
        );
        sql("UPDATE carriers SET active=false WHERE id=" + carrier);
        call(409, "POST", "/api/v1/admin/vehicles", create.replace("DEMO.001", "TEST-006"), admin);
        call(
            200,
            "PUT",
            "/api/v1/admin/vehicles/" + vehicle,
            update.replace("true", "false").replace("version\":0", "version\":1"),
            admin
        );
        assertEquals("2", sql("SELECT version FROM carrier_vehicles WHERE id=" + vehicle));
    }

    @Test
    void demoCollisionRollsBackEveryEarlierInsertAndKeepsExistingData() throws Exception {
        long carrier = call(
            201,
            "POST",
            "/api/v1/admin/carriers",
            "{\"code\":\"DEMOBACNAM\",\"name\":\"Nhà xe đang dùng\",\"phone\":\"0000000000\",\"description\":\"Giữ nguyên\"}",
            admin
        )
            .path("id")
            .asLong();
        call(409, "POST", "/api/v1/admin/demo", "{}", admin);
        assertEquals("0", sql("SELECT count(*) FROM catalog_items WHERE code LIKE 'DEMO%'"));
        assertEquals("0", sql("SELECT count(*) FROM location_provinces"));
        assertEquals("1", sql("SELECT count(*) FROM auth_data_scopes"));
        assertEquals("Giữ nguyên", sql("SELECT description FROM carriers WHERE id=" + carrier));
    }

    @Test
    void concurrentUpdatesHaveOneWinnerAndReferencedImageCannotBeDeleted() throws Exception {
        seed();
        long vehicle = id("carrier_vehicles", "DEMOVEH01");
        long type = id("catalog_items", "DEMOSEAT");
        long image = Long.parseLong(
            sql(
                "INSERT INTO media_assets(public_id,status,url,thumbnail_url,format,bytes,width,height,created_by,updated_by) VALUES ('business-test','READY','https://example.invalid/photo.jpg','https://example.invalid/thumb.jpg','jpg',1024,32,32,1,1) RETURNING id"
            )
        );
        String body =
            "{\"plateNumber\":\"DEMO-001\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":28,\"active\":true,\"version\":0,\"photoMediaId\":" +
            image +
            "}";
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/vehicles/" + vehicle)
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var second = executor.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/vehicles/" + vehicle)
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertEquals(Set.of(200, 409), Set.of(a, b));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("1", sql("SELECT version FROM carrier_vehicles WHERE id=" + vehicle));
        SecurityContextHolder.getContext().setAuthentication(admin);
        var error = assertThrows(BusinessException.class, () ->
            root.getBean(MediaService.class).delete(image, 0, (UserPrincipal) admin.getPrincipal())
        );
        assertEquals(ErrorCode.RESOURCE_IN_USE, error.getErrorCode());
        assertEquals("READY:0", sql("SELECT status || ':' || version FROM media_assets WHERE id=" + image));
    }

    @Test
    void scopeHistoryFailureAndCapacityCannotLeaveOrphanCarrierOrScope() throws Exception {
        sql(
            "CREATE FUNCTION fail_business_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''Business history test failure''; END'; CREATE TRIGGER fail_business_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_business_history()"
        );
        String body =
            "{\"code\":\"TESTCAPACITY\",\"name\":\"Test capacity\",\"phone\":\"0000000000\",\"description\":\"Test\"}";
        call(503, "POST", "/api/v1/admin/carriers", body, admin);
        assertEquals("0", sql("SELECT count(*) FROM auth_data_scopes"));
        assertEquals("0", sql("SELECT count(*) FROM carriers"));
        sql("DROP TRIGGER fail_business_history ON auth_access_changes; DROP FUNCTION fail_business_history()");
        seed();
        assertEquals("2", sql("SELECT count(*) FROM auth_access_changes WHERE event_type='SCOPE_CREATED'"));
        call(409, "POST", "/api/v1/admin/carriers", body, admin);
        assertEquals("2", sql("SELECT count(*) FROM auth_data_scopes"));
        assertEquals("2", sql("SELECT count(*) FROM carriers"));
    }

    private String routeBody(long carrier, String code, long first, long middle, long last) {
        return """
        {"carrierId":%d,"code":"%s","nameVi":"Tuyến kiểm thử","nameEn":"Test route","description":"","active":true,
         "stops":[{"locationId":%d,"arrivalOffsetMinutes":0,"departureOffsetMinutes":0,"cumulativeFare":0,"pickupAllowed":true,"dropoffAllowed":false},
                  {"locationId":%d,"arrivalOffsetMinutes":1500,"departureOffsetMinutes":1510,"cumulativeFare":100000,"pickupAllowed":true,"dropoffAllowed":true},
                  {"locationId":%d,"arrivalOffsetMinutes":1800,"departureOffsetMinutes":1800,"cumulativeFare":250000,"pickupAllowed":false,"dropoffAllowed":true}]}
        """.formatted(carrier, code, first, middle, last);
    }

    private record TripFixture(long carrier, long scope, long route, long vehicle, long driver) {}

    /** Fixture account ready chỉ ở schema test loopback; không gọi email/provider hoặc thay dữ liệu Neon. */
    private TripFixture tripFixture(boolean other) throws Exception {
        String suffix = other ? "B" : "A";
        long carrier = id("carriers", other ? "DEMOBACNAM" : "DEMOMIENNAM");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        long driver = id("carrier_drivers", other ? "DEMODRIVER03" : "DEMODRIVER01");
        long vehicle = id("carrier_vehicles", other ? "DEMOVEH03" : "DEMOVEH01");
        String username = "tripdriver" + suffix.toLowerCase(Locale.ROOT);
        long account = Long.parseLong(
            sql(
                "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email,email_verified_at) SELECT '" +
                    username +
                    "',password_hash,'Driver CI',false,'" +
                    username +
                    "@example.invalid',CURRENT_TIMESTAMP FROM auth_users WHERE id=1 RETURNING id"
            )
        );
        sql(
            "INSERT INTO auth_user_roles(user_id,role_code) VALUES(" +
                account +
                ",'DRIVER'); INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES(" +
                scope +
                "," +
                account +
                "); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                scope +
                "," +
                account +
                ",'DRIVER'); UPDATE carrier_drivers SET user_id=" +
                account +
                " WHERE id=" +
                driver
        );
        long route = call(
            201,
            "POST",
            "/api/v1/admin/routes",
            routeBody(
                carrier,
                "TRIPROUTE" + suffix,
                id("location_places", "DEMOPLACEA"),
                id("location_places", "DEMOPLACEB"),
                id("location_places", "DEMOPLACEC")
            ),
            admin
        )
            .path("id")
            .asLong();
        return new TripFixture(carrier, scope, route, vehicle, driver);
    }

    private String tripBody(TripFixture fixture, String code, Instant start) {
        return (
            "{\"code\":\"" +
            code +
            "\",\"routeId\":" +
            fixture.route() +
            ",\"vehicleId\":" +
            fixture.vehicle() +
            ",\"driverId\":" +
            fixture.driver() +
            ",\"capacity\":10,\"departureAt\":\"" +
            start +
            "\"}"
        );
    }

    private Authentication tripDriver(TripFixture fixture) throws Exception {
        sql(
            "INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'DRIVER',code FROM auth_permissions WHERE code IN('DRIVER_TRIP_VIEW','DRIVER_TRIP_READ','DRIVER_TRIP_UPDATE') ON CONFLICT DO NOTHING"
        );
        long user = Long.parseLong(sql("SELECT user_id FROM carrier_drivers WHERE id=" + fixture.driver()));
        return actor(user, sql("SELECT username FROM auth_users WHERE id=" + user), "DRIVER");
    }

    private Authentication customer(String username) throws Exception {
        sql(
            "INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'CUSTOMER',code FROM auth_permissions WHERE code IN('MY_BOOKING_VIEW','MY_BOOKING_READ','MY_BOOKING_CREATE','MY_BOOKING_UPDATE') ON CONFLICT DO NOTHING"
        );
        long user = Long.parseLong(
            sql(
                "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email,email_verified_at) SELECT '" +
                    username +
                    "',password_hash,'Customer CI',false,'" +
                    username +
                    "@example.invalid',CURRENT_TIMESTAMP FROM auth_users WHERE id=1 RETURNING id"
            )
        );
        sql("INSERT INTO auth_user_roles(user_id,role_code) VALUES(" + user + ",'CUSTOMER')");
        return actor(user, username, "CUSTOMER");
    }

    private long openBookingTrip(TripFixture fixture, String code, Instant start) throws Exception {
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, code, start), admin)
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        return id;
    }

    private String bookingBody(long trip, int from, int to, int seats, UUID key) throws Exception {
        String pickup = sql("SELECT id FROM trip_stops WHERE trip_id=" + trip + " AND stop_order=" + from),
            dropoff = sql("SELECT id FROM trip_stops WHERE trip_id=" + trip + " AND stop_order=" + to);
        return (
            "{\"tripId\":" +
            trip +
            ",\"pickupStopId\":" +
            pickup +
            ",\"dropoffStopId\":" +
            dropoff +
            ",\"seats\":" +
            seats +
            ",\"contactName\":\"Liên hệ TEST\",\"contactPhone\":\"0000000000\",\"requestKey\":\"" +
            key +
            "\"}"
        );
    }

    @Test
    void bookingStaffScopeFiltersCountIdsMutationsAndCustomerReadRevokesImmediately() throws Exception {
        seed();
        var own = tripFixture(false);
        var other = tripFixture(true);
        var buyer = customer("scopedbuyer");
        var staff = member(
            own.scope(),
            "BOOK_CI",
            "'BOOKING_VIEW','BOOKING_READ','BOOKING_CREATE','BOOKING_UPDATE'",
            "bookstaff"
        );
        long trip = openBookingTrip(own, "SCOPEDBOOK", Instant.now().plus(2, ChronoUnit.DAYS)),
            foreign = openBookingTrip(other, "FOREIGNBOOK", Instant.now().plus(2, ChronoUnit.DAYS));
        long id = call(201, "POST", "/api/v1/my-bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), buyer)
            .path("id")
            .asLong();
        long foreignId = call(
            201,
            "POST",
            "/api/v1/admin/bookings",
            bookingBody(foreign, 1, 3, 1, UUID.randomUUID()),
            admin
        )
            .path("id")
            .asLong();
        assertEquals(1, call(200, "GET", "/api/v1/admin/bookings?size=1", null, staff).path("totalElements").asInt());
        call(404, "GET", "/api/v1/admin/bookings/" + foreignId, null, staff);
        assertEquals(1, call(200, "GET", "/api/v1/admin/bookings/trips", null, staff).path("totalElements").asInt());
        call(
            404,
            "PUT",
            "/api/v1/admin/bookings/" + foreignId + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            staff
        );
        call(403, "POST", "/api/v1/admin/bookings", bookingBody(foreign, 1, 2, 1, UUID.randomUUID()), staff);
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + id + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            staff
        );
        sql("DELETE FROM auth_role_permissions WHERE role_code='CUSTOMER' AND permission_code='MY_BOOKING_READ'");
        call(403, "GET", "/api/v1/my-bookings", null, buyer);
        call(403, "GET", "/api/v1/my-bookings/" + id, null, buyer);
        sql(
            "UPDATE auth_scope_memberships SET active=false WHERE scope_id=" +
                own.scope() +
                " AND user_id=(SELECT id FROM auth_users WHERE username='bookstaff')"
        );
        assertEquals(0, call(200, "GET", "/api/v1/admin/bookings", null, staff).path("totalElements").asInt());
    }

    @Test
    void publicSearchUsesPickupLocalDateDirectionAndRemainingSegmentsWithoutPrivateFields() throws Exception {
        seed();
        var fixture = tripFixture(false);
        Instant start = Instant.now()
            .plus(3, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.DAYS)
            .plusSeconds(16 * 3600);
        long trip = openBookingTrip(fixture, "SEARCHOVERNIGHT", start);
        long middle = id("location_places", "DEMOPLACEB"),
            last = id("location_places", "DEMOPLACEC");
        var pickupDate = start
            .plusSeconds(1510 * 60L)
            .atZone(ZoneId.of(Business.TIME_ZONE))
            .toLocalDate();
        String path = "/api/v1/trips?fromLocationId=" + middle + "&toLocationId=" + last + "&date=" + pickupDate;
        var response = mvc.perform(request(HttpMethod.GET, path)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var result = json.readTree(response.getContentAsString()).path("data");
        assertEquals(1, result.path("totalElements").asInt());
        assertEquals(150000, result.at("/content/0/unitPrice").asInt());
        assertEquals(
            0,
            call(200, "GET", path.replace(pickupDate.toString(), pickupDate.minusDays(1).toString()), null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(
                200,
                "GET",
                "/api/v1/trips?fromLocationId=" + last + "&toLocationId=" + middle + "&date=" + pickupDate,
                null,
                admin
            )
                .path("totalElements")
                .asInt()
        );
        call(400, "GET", "/api/v1/trips?date=" + pickupDate, null, admin);
        var detail = call(200, "GET", "/api/v1/trips/" + trip, null, admin);
        assertFalse(detail.has("driverUserId"));
        assertFalse(detail.has("driverPhone"));
        assertFalse(detail.has("createdBy"));
        assertFalse(detail.has("contactPhone"));
        long pickupId = detail.at("/stops/1/id").asLong(),
            dropoffId = detail.at("/stops/2/id").asLong();
        String quotePath =
            "/api/v1/trips/" + trip + "/quote?pickupStopId=" + pickupId + "&dropoffStopId=" + dropoffId + "&seats=2";
        var quote = call(200, "GET", quotePath, null, admin);
        assertEquals(300000, quote.path("totalPrice").asInt());
        assertEquals(10, quote.path("availableSeats").asInt());
        assertTrue(quote.path("accepting").asBoolean());
        sql("UPDATE trip_segments SET reserved_seats=10 WHERE trip_id=" + trip + " AND segment_order=2");
        assertEquals(0, call(200, "GET", path, null, admin).path("totalElements").asInt());
        sql("UPDATE trip_segments SET reserved_seats=0 WHERE trip_id=" + trip);
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + trip + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Dừng chuyến\"}",
            admin
        );
        assertEquals(0, call(200, "GET", path, null, admin).path("totalElements").asInt());
        assertEquals(
            "CANCELLED",
            call(200, "GET", "/api/v1/trips/" + trip, null, admin)
                .path("status")
                .asText()
        );
    }

    @Test
    void websiteBookingsKeepPriceAreOwnerBoundIdempotentAndOnlyOneMayRemainActive() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var owner = customer("bookcustomer");
        var stranger = customer("bookstranger");
        long trip = openBookingTrip(fixture, "BOOKWEB", Instant.now().plus(2, ChronoUnit.DAYS));
        UUID key = UUID.randomUUID();
        String body = bookingBody(trip, 1, 3, 3, key);
        var created = call(201, "POST", "/api/v1/my-bookings", body, owner);
        long id = created.path("id").asLong();
        assertEquals("WEB", created.path("source").asText());
        assertEquals("PENDING", created.path("status").asText());
        assertEquals(750000, created.path("totalPrice").asInt());
        assertEquals(id, call(201, "POST", "/api/v1/my-bookings", body, owner).path("id").asLong());
        assertEquals("0", sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip));
        call(409, "POST", "/api/v1/my-bookings", body.replace("\"seats\":3", "\"seats\":2"), owner);
        call(409, "POST", "/api/v1/my-bookings", bookingBody(trip, 1, 2, 1, UUID.randomUUID()), owner);
        call(404, "GET", "/api/v1/my-bookings/" + id, null, stranger);
        call(
            404,
            "PUT",
            "/api/v1/my-bookings/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":0,\"note\":\"Không phải chủ\"}",
            stranger
        );
        call(
            409,
            "PUT",
            "/api/v1/my-bookings/" + id + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            owner
        );
        call(400, "POST", "/api/v1/my-bookings", body.replace("\"tripId\":", "\"unitPrice\":1,\"tripId\":"), owner);
        sql(
            "UPDATE carrier_route_stops SET cumulative_fare=cumulative_fare+100000 WHERE route_id=" +
                fixture.route() +
                " AND stop_order>1"
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + id + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        assertEquals(
            750000,
            call(200, "GET", "/api/v1/my-bookings/" + id, null, owner)
                .path("totalPrice")
                .asInt()
        );
        call(
            200,
            "PUT",
            "/api/v1/my-bookings/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Đổi kế hoạch\"}",
            owner
        );
        call(
            409,
            "PUT",
            "/api/v1/my-bookings/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Lặp\"}",
            owner
        );
        assertEquals("0", sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip));
        call(201, "POST", "/api/v1/my-bookings", bookingBody(trip, 1, 2, 1, UUID.randomUUID()), owner);
        call(200, "GET", "/my-bookings?lang=vi", null, owner);
        call(200, "GET", "/my-bookings?lang=en", null, owner);
    }

    @Test
    void concurrentWebRequestsAcrossTripsLeaveOnlyOneActiveBooking() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var owner = customer("parallelcustomer");
        long first = openBookingTrip(fixture, "WEBRACEONE", Instant.now().plus(2, ChronoUnit.DAYS));
        long second = openBookingTrip(fixture, "WEBRACETWO", Instant.now().plus(4, ChronoUnit.DAYS));
        String firstBody = bookingBody(first, 1, 2, 1, UUID.randomUUID());
        String secondBody = bookingBody(second, 1, 2, 1, UUID.randomUUID());
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> {
                start.await();
                return mutationStatus(HttpMethod.POST, "/api/v1/my-bookings", firstBody, owner);
            });
            var two = workers.submit(() -> {
                start.await();
                return mutationStatus(HttpMethod.POST, "/api/v1/my-bookings", secondBody, owner);
            });
            start.countDown();
            assertEquals(Set.of(201, 409), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        assertEquals("1", sql("SELECT count(*) FROM bookings WHERE source_code='WEB' AND status='PENDING'"));
        assertEquals("0", sql("SELECT sum(reserved_seats) FROM trip_segments"));
    }

    @Test
    void concurrentTripCancellationCannotLeaveConfirmedBookingOrReservedSeats() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "CANCELCONFIRMRACE", Instant.now().plus(2, ChronoUnit.DAYS));
        long id = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 4, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var confirm = workers.submit(() -> {
                start.await();
                return mutationStatus(
                    HttpMethod.PUT,
                    "/api/v1/admin/bookings/" + id + "/state",
                    "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
                    admin
                );
            });
            var cancel = workers.submit(() -> {
                start.await();
                return mutationStatus(
                    HttpMethod.PUT,
                    "/api/v1/admin/trips/" + trip + "/state",
                    "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy đồng thời TEST\"}",
                    admin
                );
            });
            start.countDown();
            assertEquals(200, cancel.get(20, TimeUnit.SECONDS));
            assertTrue(Set.of(200, 409).contains(confirm.get(20, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        assertEquals("CANCELLED", sql("SELECT status FROM carrier_trips WHERE id=" + trip));
        assertEquals("CANCELLED", sql("SELECT status FROM bookings WHERE id=" + id));
        assertEquals("0", sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip));
        assertEquals(
            "1",
            sql("SELECT count(*) FROM booking_events WHERE booking_id=" + id + " AND event_type='CANCELLED'")
        );
    }

    @Test
    void concurrentDepartureAndConfirmationCannotConfirmAfterPickupCloses() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var driver = tripDriver(fixture);
        long trip = openBookingTrip(fixture, "DEPARTCONFIRMRACE", Instant.now().plusSeconds(120));
        long id = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 4, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        int confirmed;
        try {
            var confirm = workers.submit(() -> {
                start.await();
                return mutationStatus(
                    HttpMethod.PUT,
                    "/api/v1/admin/bookings/" + id + "/state",
                    "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
                    admin
                );
            });
            var depart = workers.submit(() -> {
                start.await();
                return mutationStatus(
                    HttpMethod.PUT,
                    "/api/v1/driver/trips/" + trip + "/progress",
                    "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"\"}",
                    driver
                );
            });
            start.countDown();
            assertEquals(200, depart.get(20, TimeUnit.SECONDS));
            confirmed = confirm.get(20, TimeUnit.SECONDS);
            assertTrue(Set.of(200, 409).contains(confirmed));
        } finally {
            workers.shutdownNow();
        }
        assertEquals("IN_PROGRESS", sql("SELECT status FROM carrier_trips WHERE id=" + trip));
        assertEquals(confirmed == 200 ? "CONFIRMED" : "REJECTED", sql("SELECT status FROM bookings WHERE id=" + id));
        assertEquals(
            confirmed == 200 ? "8" : "0",
            sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip)
        );
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM trip_stops WHERE trip_id=" +
                    trip +
                    " AND stop_order=1 AND actual_departure_at IS NOT NULL"
            )
        );
    }

    /** Dùng trong hai phiên đồng thời; trả status thật để kiểm cả hai thứ tự commit hợp lệ. */
    private int mutationStatus(HttpMethod method, String path, String body, Authentication actor) throws Exception {
        return mvc
            .perform(
                request(method, path)
                    .with(authentication(actor))
                    .with(csrf())
                    .contentType("application/json")
                    .content(body)
            )
            .andReturn()
            .getResponse()
            .getStatus();
    }

    @Test
    void confirmationRaceCannotOversellAndSeparateSegmentsReuseSeats() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "CAPACITYRACE", Instant.now().plus(2, ChronoUnit.DAYS));
        long first = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 2, 7, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        long second = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 2, 7, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        var workers = Executors.newFixedThreadPool(2);
        String body = "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}";
        try {
            var one = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/bookings/" + first + "/state")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var two = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/bookings/" + second + "/state")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(Set.of(200, 409), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        long last = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 2, 3, 10, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        call(200, "PUT", "/api/v1/admin/bookings/" + last + "/state", body, admin);
        assertEquals(
            "7",
            sql("SELECT reserved_seats FROM trip_segments WHERE trip_id=" + trip + " AND segment_order=1")
        );
        assertEquals(
            "10",
            sql("SELECT reserved_seats FROM trip_segments WHERE trip_id=" + trip + " AND segment_order=2")
        );
        call(409, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), admin);
    }

    @Test
    void tripCancellationAndBookingHistoryFailOrCommitTogetherWithCapacity() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "CANCELBOOKINGS", Instant.now().plus(2, ChronoUnit.DAYS));
        long confirmed = call(
            201,
            "POST",
            "/api/v1/admin/bookings",
            bookingBody(trip, 1, 3, 5, UUID.randomUUID()),
            admin
        )
            .path("id")
            .asLong();
        long pending = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 2, 3, 2, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + confirmed + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        sql(
            "CREATE FUNCTION fail_booking_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test booking event failure''; END'; CREATE TRIGGER fail_booking_event BEFORE INSERT ON booking_events FOR EACH ROW EXECUTE FUNCTION fail_booking_event()"
        );
        call(
            503,
            "PUT",
            "/api/v1/admin/trips/" + trip + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy chuyến\"}",
            admin
        );
        assertEquals("OPEN", sql("SELECT status FROM carrier_trips WHERE id=" + trip));
        assertEquals("10", sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip));
        assertEquals("CONFIRMED", sql("SELECT status FROM bookings WHERE id=" + confirmed));
        assertEquals("PENDING", sql("SELECT status FROM bookings WHERE id=" + pending));
        call(503, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 2, 1, UUID.randomUUID()), admin);
        assertEquals("2", sql("SELECT count(*) FROM bookings"));
        sql("DROP TRIGGER fail_booking_event ON booking_events");
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + trip + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy chuyến\"}",
            admin
        );
        assertEquals("0", sql("SELECT sum(reserved_seats) FROM trip_segments WHERE trip_id=" + trip));
        assertEquals("2", sql("SELECT count(*) FROM bookings WHERE trip_id=" + trip + " AND status='CANCELLED'"));
        call(
            409,
            "PUT",
            "/api/v1/admin/bookings/" + pending + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":1,\"note\":\"\"}",
            admin
        );
    }

    @Test
    void manualPaymentsAndRefundsAreBoundedIdempotentAndDoNotFollowCancellationAutomatically() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "MONEYBOOKING", Instant.now().plus(2, ChronoUnit.DAYS));
        long id = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 2, 1, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        String payment =
            "{\"kind\":\"PAYMENT\",\"amount\":100000,\"note\":\"Đã thu trực tiếp TEST\",\"version\":1,\"requestKey\":\"" +
            UUID.randomUUID() +
            "\"}";
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + id + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        assertEquals(
            100000,
            call(200, "PUT", "/api/v1/admin/bookings/" + id + "/payment", payment, admin)
                .path("paidAmount")
                .asInt()
        );
        assertEquals(
            100000,
            call(200, "PUT", "/api/v1/admin/bookings/" + id + "/payment", payment, admin)
                .path("paidAmount")
                .asInt()
        );
        call(409, "PUT", "/api/v1/admin/bookings/" + id + "/payment", payment.replace("100000", "1"), admin);
        var cancelled = call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":2,\"note\":\"Khách hủy\"}",
            admin
        );
        assertTrue(cancelled.path("refundDue").asBoolean());
        assertEquals(0, cancelled.path("refundedAmount").asInt());
        String refund =
            "{\"kind\":\"REFUND\",\"amount\":100000,\"note\":\"Đã hoàn trực tiếp TEST\",\"version\":3,\"requestKey\":\"" +
            UUID.randomUUID() +
            "\"}";
        call(400, "PUT", "/api/v1/admin/bookings/" + id + "/payment", refund.replace("100000", "100001"), admin);
        assertFalse(
            call(200, "PUT", "/api/v1/admin/bookings/" + id + "/payment", refund, admin)
                .path("refundDue")
                .asBoolean()
        );
        assertEquals(
            100000,
            call(200, "PUT", "/api/v1/admin/bookings/" + id + "/payment", refund, admin)
                .path("refundedAmount")
                .asInt()
        );
    }

    @Test
    void driverDepartureRejectsPendingAndArrivalCompletesConfirmedWithoutReleasingHistoricalSegments()
        throws Exception {
        seed();
        var fixture = tripFixture(false);
        var driver = tripDriver(fixture);
        long trip = openBookingTrip(fixture, "DRIVERBOOKINGS", Instant.now().plusSeconds(120));
        long pending = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        long confirmed = call(
            201,
            "POST",
            "/api/v1/admin/bookings",
            bookingBody(trip, 1, 2, 4, UUID.randomUUID()),
            admin
        )
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + confirmed + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        var passengerList = call(200, "GET", "/api/v1/driver/trips/" + trip + "/passengers?size=1", null, driver);
        assertEquals(1, passengerList.path("totalElements").asInt());
        assertFalse(passengerList.at("/content/0").has("customerId"));
        assertFalse(passengerList.at("/content/0").has("totalPrice"));
        call(404, "GET", "/api/v1/driver/trips/" + trip + "/passengers", null, admin);
        call(
            200,
            "PUT",
            "/api/v1/driver/trips/" + trip + "/progress",
            "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"\"}",
            driver
        );
        assertEquals("REJECTED", sql("SELECT status FROM bookings WHERE id=" + pending));
        call(
            409,
            "PUT",
            "/api/v1/admin/bookings/" + confirmed + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Đã rời điểm\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/driver/trips/" + trip + "/progress",
            "{\"action\":\"ARRIVE\",\"stopOrder\":2,\"version\":2,\"note\":\"\"}",
            driver
        );
        assertEquals("COMPLETED", sql("SELECT status FROM bookings WHERE id=" + confirmed));
        assertEquals(
            "4",
            sql("SELECT reserved_seats FROM trip_segments WHERE trip_id=" + trip + " AND segment_order=1")
        );
        call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 2, 3, 10, UUID.randomUUID()), admin);
    }

    @Test
    void driverSeesOnlyCurrentAssignedNonDraftTripsAndScopeRevocationAppliesImmediately() throws Exception {
        seed();
        var own = tripFixture(false);
        var other = tripFixture(true);
        var driver = tripDriver(own);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(own, "DRIVERASSIGNED", start), admin)
            .path("id")
            .asLong();
        long foreign = call(201, "POST", "/api/v1/admin/trips", tripBody(other, "DRIVERFOREIGN", start), admin)
            .path("id")
            .asLong();
        assertEquals(0, call(200, "GET", "/api/v1/driver/trips", null, driver).path("totalElements").asInt());
        call(404, "GET", "/api/v1/driver/trips/" + id, null, driver);
        for (long trip : new long[] { id, foreign })
            call(
                200,
                "PUT",
                "/api/v1/admin/trips/" + trip + "/state",
                "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
                admin
            );
        assertEquals(1, call(200, "GET", "/api/v1/driver/trips?size=1", null, driver).path("totalElements").asInt());
        call(200, "GET", "/api/v1/driver/trips/" + id, null, driver);
        call(404, "GET", "/api/v1/driver/trips/" + foreign, null, driver);
        call(404, "GET", "/api/v1/driver/trips/" + id, null, admin);
        call(
            404,
            "PUT",
            "/api/v1/driver/trips/" + foreign + "/progress",
            "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"\"}",
            driver
        );
        call(200, "GET", "/driver/trips?lang=vi", null, driver);
        call(200, "GET", "/driver/trips?lang=en", null, driver);
        sql("DELETE FROM auth_role_permissions WHERE role_code='DRIVER' AND permission_code='DRIVER_TRIP_VIEW'");
        call(403, "GET", "/driver/trips", null, driver);
        call(200, "GET", "/api/v1/driver/trips", null, driver);
        sql("UPDATE carrier_drivers SET user_id=NULL WHERE id=" + own.driver());
        assertEquals(0, call(200, "GET", "/api/v1/driver/trips", null, driver).path("totalElements").asInt());
        call(404, "GET", "/api/v1/driver/trips/" + id, null, driver);
    }

    @Test
    void driverProgressEnforcesStopOrderVersionTimeAndTerminalState() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var driver = tripDriver(fixture);
        Instant start = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, "DRIVERPROGRESS", start), admin)
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        String endpoint = "/api/v1/driver/trips/" + id + "/progress";
        call(409, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":2,\"version\":1,\"note\":\"\"}", driver);
        var started = call(
            200,
            "PUT",
            endpoint,
            "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"Xuất phát\"}",
            driver
        );
        assertEquals("IN_PROGRESS", started.path("status").asText());
        assertFalse(started.at("/stops/0/actualDepartureAt").isNull());
        call(409, "PUT", endpoint, "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"\"}", driver);
        call(409, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":3,\"version\":2,\"note\":\"\"}", driver);
        call(409, "PUT", endpoint, "{\"action\":\"DEPART\",\"stopOrder\":2,\"version\":2,\"note\":\"\"}", driver);
        call(200, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":2,\"version\":2,\"note\":\"\"}", driver);
        call(409, "PUT", endpoint, "{\"action\":\"COMPLETE\",\"stopOrder\":3,\"version\":3,\"note\":\"\"}", driver);
        call(200, "PUT", endpoint, "{\"action\":\"DEPART\",\"stopOrder\":2,\"version\":3,\"note\":\"\"}", driver);
        call(200, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":3,\"version\":4,\"note\":\"\"}", driver);
        var completed = call(
            200,
            "PUT",
            endpoint,
            "{\"action\":\"COMPLETE\",\"stopOrder\":3,\"version\":5,\"note\":\"Đã đến cuối tuyến\"}",
            driver
        );
        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals(7, completed.path("events").size());
        call(409, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":3,\"version\":6,\"note\":\"\"}", driver);
        assertEquals(
            "3",
            sql(
                "SELECT count(*) FROM trip_stops WHERE trip_id=" +
                    id +
                    " AND actual_arrival_at IS NOT NULL AND actual_departure_at IS NOT NULL"
            )
        );
    }

    @Test
    void driverProgressHistoryFailureRollsBackAndConcurrentStartOnlyAdvancesOnce() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var driver = tripDriver(fixture);
        Instant start = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, "DRIVERRACE", start), admin)
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        String endpoint = "/api/v1/driver/trips/" + id + "/progress",
            body = "{\"action\":\"START\",\"stopOrder\":1,\"version\":1,\"note\":\"\"}";
        sql(
            "CREATE FUNCTION fail_driver_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test driver event failure''; END'; CREATE TRIGGER fail_driver_event BEFORE INSERT ON trip_events FOR EACH ROW EXECUTE FUNCTION fail_driver_event()"
        );
        call(503, "PUT", endpoint, body, driver);
        call(503, "PUT", "/api/v1/admin/trips/" + id + "/notice", "{\"note\":\"Thông báo thử\",\"version\":1}", admin);
        assertEquals("OPEN:1", sql("SELECT status || ':' || version FROM carrier_trips WHERE id=" + id));
        assertEquals(
            "0",
            sql("SELECT count(*) FROM trip_stops WHERE trip_id=" + id + " AND actual_arrival_at IS NOT NULL")
        );
        sql("DROP TRIGGER fail_driver_event ON trip_events");
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, endpoint)
                            .with(authentication(driver))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var two = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, endpoint)
                            .with(authentication(driver))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(Set.of(200, 409), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        assertEquals("1", sql("SELECT count(*) FROM trip_events WHERE trip_id=" + id + " AND event_type='STARTED'"));
        var notice = call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/notice",
            "{\"note\":\"Sự cố, liên hệ điều hành\",\"version\":2}",
            admin
        );
        assertEquals("IN_PROGRESS", notice.path("status").asText());
        assertEquals(3, notice.path("version").asInt());
        call(
            409,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":3,\"note\":\"Không hủy chuyến đang chạy\"}",
            admin
        );
        call(409, "PUT", endpoint, "{\"action\":\"ARRIVE\",\"stopOrder\":2,\"version\":2,\"note\":\"\"}", driver);
    }

    @Test
    void tripsSnapshotAcrossDaysValidateAssetsAndRequireVersionAndState() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var other = tripFixture(true);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var trip = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, "SNAPSHOTTRIP", start), admin);
        long id = trip.path("id").asLong();
        assertEquals("DRAFT", trip.path("status").asText());
        assertEquals(3, trip.path("stops").size());
        assertEquals(start.plusSeconds(1500 * 60L).toString(), trip.at("/stops/1/scheduledArrivalAt").asText());
        assertEquals("2", sql("SELECT count(*) FROM trip_segments WHERE trip_id=" + id));
        String snapshot = trip.at("/stops/1/nameVi").asText();
        sql(
            "UPDATE location_places SET name_vi='Tên mới' WHERE code='DEMOPLACEB'; UPDATE carrier_route_stops SET cumulative_fare=200000 WHERE route_id=" +
                fixture.route() +
                " AND stop_order=2"
        );
        var detail = call(200, "GET", "/api/v1/admin/trips/" + id, null, admin);
        assertEquals(snapshot, detail.at("/stops/1/nameVi").asText());
        assertEquals(100000, detail.at("/stops/1/cumulativeFare").asInt());
        call(
            400,
            "POST",
            "/api/v1/admin/trips",
            tripBody(fixture, "WRONGVEHICLE", start).replace(
                "\"vehicleId\":" + fixture.vehicle(),
                "\"vehicleId\":" + other.vehicle()
            ),
            admin
        );
        call(400, "POST", "/api/v1/admin/trips", tripBody(fixture, "PASTTRIP", Instant.now().minusSeconds(60)), admin);
        call(
            409,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"IN_PROGRESS\",\"version\":0,\"note\":\"\"}",
            admin
        );
        sql("UPDATE carrier_drivers SET active=false WHERE id=" + fixture.driver());
        call(
            400,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        sql("UPDATE carrier_drivers SET active=true WHERE id=" + fixture.driver());
        var opened = call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"Mở bán\"}",
            admin
        );
        assertEquals(1, opened.path("version").asInt());
        call(
            409,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":0,\"note\":\"Hủy\"}",
            admin
        );
        call(
            409,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy thử\"}",
            admin
        );
        call(
            409,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":2,\"note\":\"\"}",
            admin
        );
        call(200, "GET", "/operations/trips?lang=vi", null, admin);
        call(200, "GET", "/operations/trips?lang=en", null, admin);
    }

    @Test
    void concurrentTripPublicationSerializesSharedVehicleAndDriverSchedules() throws Exception {
        seed();
        var fixture = tripFixture(false);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long first = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, "RACEFIRST", start), admin)
            .path("id")
            .asLong();
        long second = call(
            201,
            "POST",
            "/api/v1/admin/trips",
            tripBody(fixture, "RACESECOND", start.plusSeconds(3600)),
            admin
        )
            .path("id")
            .asLong();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/trips/" + first + "/state")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var two = workers.submit(() ->
                mvc
                    .perform(
                        request(HttpMethod.PUT, "/api/v1/admin/trips/" + second + "/state")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(Set.of(200, 409), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        assertEquals("1", sql("SELECT count(*) FROM carrier_trips WHERE status='OPEN'"));
        long open = Long.parseLong(sql("SELECT id FROM carrier_trips WHERE status='OPEN'")),
            draft = Long.parseLong(sql("SELECT id FROM carrier_trips WHERE status='DRAFT'"));
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + open + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Giải phóng lịch\"}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + draft + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
    }

    @Test
    void tripScopeFiltersCountIdsPickerAndIndependentViewWithoutOtherCrudRights() throws Exception {
        seed();
        var own = tripFixture(false);
        var other = tripFixture(true);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(own, "OWNTRIP", start), admin)
            .path("id")
            .asLong();
        long foreign = call(201, "POST", "/api/v1/admin/trips", tripBody(other, "FOREIGNTRIP", start), admin)
            .path("id")
            .asLong();
        var staff = member(own.scope(), "TRIP_CI", "'TRIP_VIEW','TRIP_READ','TRIP_CREATE','TRIP_UPDATE'", "tripstaff");
        assertEquals(1, call(200, "GET", "/api/v1/admin/trips?size=1", null, staff).path("totalElements").asInt());
        call(200, "GET", "/api/v1/admin/trips/" + id, null, staff);
        call(404, "GET", "/api/v1/admin/trips/" + foreign, null, staff);
        call(404, "POST", "/api/v1/admin/trips", tripBody(other, "DENIEDTRIP", start), staff);
        assertEquals(
            1,
            call(200, "GET", "/api/v1/admin/trips/choices?kind=CARRIER", null, staff).path("totalElements").asInt()
        );
        assertEquals(
            0,
            call(200, "GET", "/api/v1/admin/trips/choices?kind=VEHICLE&carrierId=" + other.carrier(), null, staff)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            2,
            call(
                200,
                "GET",
                "/api/v1/admin/trips/choices?kind=VEHICLE&tripId=" + id + "&carrierId=" + other.carrier(),
                null,
                staff
            )
                .path("totalElements")
                .asInt()
        );
        call(404, "GET", "/api/v1/admin/trips/choices?kind=VEHICLE&tripId=" + foreign, null, staff);
        call(200, "GET", "/operations/trips", null, staff);
        sql("DELETE FROM auth_role_permissions WHERE role_code='TRIP_CI' AND permission_code='TRIP_VIEW'");
        call(403, "GET", "/operations/trips", null, staff);
        call(200, "GET", "/api/v1/admin/trips", null, staff);
        sql(
            "UPDATE auth_scope_memberships SET active=false WHERE scope_id=" +
                own.scope() +
                " AND user_id=(SELECT id FROM auth_users WHERE username='tripstaff')"
        );
        assertEquals(0, call(200, "GET", "/api/v1/admin/trips", null, staff).path("totalElements").asInt());
    }

    @Test
    void tripAssignmentProtectsReservedCapacityAndHistoryFailureRollsBackAllWrites() throws Exception {
        seed();
        var fixture = tripFixture(false);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long id = call(201, "POST", "/api/v1/admin/trips", tripBody(fixture, "ROLLBACKTRIP", start), admin)
            .path("id")
            .asLong();
        sql("UPDATE trip_segments SET reserved_seats=5 WHERE trip_id=" + id + " AND segment_order=1");
        String assignment =
            "{\"vehicleId\":" +
            fixture.vehicle() +
            ",\"driverId\":" +
            fixture.driver() +
            ",\"capacity\":4,\"departureAt\":\"" +
            start.plusSeconds(3600) +
            "\",\"version\":0,\"note\":\"Đổi\"}";
        call(400, "PUT", "/api/v1/admin/trips/" + id + "/assignment", assignment, admin);
        sql(
            "CREATE FUNCTION fail_trip_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test trip event failure''; END'; CREATE TRIGGER fail_trip_event BEFORE INSERT ON trip_events FOR EACH ROW EXECUTE FUNCTION fail_trip_event()"
        );
        call(
            503,
            "PUT",
            "/api/v1/admin/trips/" + id + "/assignment",
            assignment.replace("\"capacity\":4", "\"capacity\":8"),
            admin
        );
        call(
            503,
            "PUT",
            "/api/v1/admin/trips/" + id + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        call(503, "POST", "/api/v1/admin/trips", tripBody(fixture, "FAILEDCREATE", start), admin);
        assertEquals("1", sql("SELECT count(*) FROM carrier_trips"));
        assertEquals("3", sql("SELECT count(*) FROM trip_stops"));
        assertEquals("2", sql("SELECT count(*) FROM trip_segments"));
        assertEquals(
            "0:DRAFT:10",
            sql("SELECT version || ':' || status || ':' || capacity FROM carrier_trips WHERE id=" + id)
        );
        assertEquals(
            "10:5",
            sql(
                "SELECT capacity || ':' || reserved_seats FROM trip_segments WHERE trip_id=" +
                    id +
                    " AND segment_order=1"
            )
        );
        assertEquals(
            start.toString(),
            call(200, "GET", "/api/v1/admin/trips/" + id, null, admin)
                .path("departureAt")
                .asText()
        );
    }

    @Test
    void routesValidateStopOrderFareParentsAndAtomicVersion() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM"),
            first = id("location_places", "DEMOPLACEA"),
            middle = id("location_places", "DEMOPLACEB"),
            last = id("location_places", "DEMOPLACEC");
        String body = routeBody(carrier, "TESTROUTE", first, middle, last);
        var route = call(201, "POST", "/api/v1/admin/routes", body, admin);
        long routeId = route.path("id").asLong();
        assertEquals(3, route.path("stopCount").asInt());
        assertEquals(1500, route.at("/stops/1/arrivalOffsetMinutes").asInt());
        call(
            400,
            "POST",
            "/api/v1/admin/routes",
            body.replace("TESTROUTE", "BADFARE").replace("250000", "50000"),
            admin
        );
        call(400, "POST", "/api/v1/admin/routes", body.replace("TESTROUTE", "BADTIME").replace("1800", "1499"), admin);
        call(400, "POST", "/api/v1/admin/routes", routeBody(carrier, "BADPLACE", first, middle, middle), admin);
        assertEquals("1", sql("SELECT count(*) FROM carrier_routes"));
        String changed = body.replace("\"active\":true", "\"version\":0,\"active\":false");
        var updated = call(200, "PUT", "/api/v1/admin/routes/" + routeId, changed, admin);
        assertEquals(1, updated.path("version").asInt());
        call(409, "PUT", "/api/v1/admin/routes/" + routeId, changed, admin);
        call(400, "PUT", "/api/v1/admin/routes/" + routeId, changed.replace("TESTROUTE", "DIFFERENT"), admin);
        assertEquals("3", sql("SELECT count(*) FROM carrier_route_stops WHERE route_id=" + routeId));
        sql(
            "UPDATE location_provinces SET active=false WHERE id=(SELECT province_id FROM location_places WHERE id=" +
                first +
                ")"
        );
        call(409, "POST", "/api/v1/admin/routes", body.replace("TESTROUTE", "OFFPARENT"), admin);
        assertEquals("1", sql("SELECT count(*) FROM carrier_routes"));
    }

    @Test
    void routesFilterScopeAtCountIdPickerAndRespectIndependentView() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM"),
            other = id("carriers", "DEMOBACNAM"),
            first = id("location_places", "DEMOPLACEA"),
            middle = id("location_places", "DEMOPLACEB"),
            last = id("location_places", "DEMOPLACEC");
        long owned = call(
            201,
            "POST",
            "/api/v1/admin/routes",
            routeBody(carrier, "OWNROUTE", first, middle, last),
            admin
        )
            .path("id")
            .asLong();
        long foreign = call(
            201,
            "POST",
            "/api/v1/admin/routes",
            routeBody(other, "OTHERROUTE", first, middle, last),
            admin
        )
            .path("id")
            .asLong();
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var staff = member(scope, "ROUTE_CI", "'ROUTE_VIEW','ROUTE_READ','ROUTE_CREATE','ROUTE_UPDATE'", "routestaff");
        assertEquals(1, call(200, "GET", "/api/v1/admin/routes?size=1", null, staff).path("totalElements").asInt());
        call(200, "GET", "/api/v1/admin/routes/" + owned, null, staff);
        call(404, "GET", "/api/v1/admin/routes/" + foreign, null, staff);
        call(404, "POST", "/api/v1/admin/routes", routeBody(other, "WRONGSCOPE", first, middle, last), staff);
        call(
            404,
            "PUT",
            "/api/v1/admin/routes/" + foreign,
            routeBody(other, "OTHERROUTE", first, middle, last).replace(
                "\"active\":true",
                "\"version\":0,\"active\":true"
            ),
            staff
        );
        assertEquals(1, call(200, "GET", "/api/v1/admin/routes/carriers", null, staff).path("totalElements").asInt());
        call(200, "GET", "/operations/routes?lang=vi", null, staff);
        sql("DELETE FROM auth_role_permissions WHERE role_code='ROUTE_CI' AND permission_code='ROUTE_VIEW'");
        call(403, "GET", "/operations/routes", null, staff);
        call(200, "GET", "/api/v1/admin/routes", null, staff);
        sql("UPDATE auth_scope_memberships SET active=false WHERE scope_id=" + scope);
        assertEquals(0, call(200, "GET", "/api/v1/admin/routes", null, staff).path("totalElements").asInt());
        call(404, "GET", "/api/v1/admin/routes/" + owned, null, staff);
    }

    @Test
    void routeStopFailureRollsBackParentAndExistingStopSet() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM"),
            first = id("location_places", "DEMOPLACEA"),
            middle = id("location_places", "DEMOPLACEB"),
            last = id("location_places", "DEMOPLACEC");
        String body = routeBody(carrier, "ROLLBACKROUTE", first, middle, last);
        long route = call(201, "POST", "/api/v1/admin/routes", body, admin).path("id").asLong();
        sql(
            "CREATE FUNCTION fail_route_stop() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test stop write failure''; END'; CREATE TRIGGER fail_route_stop BEFORE INSERT ON carrier_route_stops FOR EACH ROW EXECUTE FUNCTION fail_route_stop()"
        );
        call(503, "POST", "/api/v1/admin/routes", body.replace("ROLLBACKROUTE", "FAILEDCREATE"), admin);
        call(
            503,
            "PUT",
            "/api/v1/admin/routes/" + route,
            body.replace("\"active\":true", "\"version\":0,\"active\":false"),
            admin
        );
        assertEquals("1", sql("SELECT count(*) FROM carrier_routes"));
        assertEquals(
            "0:true:3",
            sql(
                "SELECT version || ':' || active || ':' || (SELECT count(*) FROM carrier_route_stops WHERE route_id=r.id) FROM carrier_routes r WHERE id=" +
                    route
            )
        );
    }
}
