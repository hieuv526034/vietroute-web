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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.DriverAccountCreateRequest;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.auth.dto.response.AccessChangeStateResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessChangeRepository;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.RoleResourceRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.business.booking.repository.BookingLifecycleRepository;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.service.DriverService;
import vn.vietroute.business.carrier.service.VehicleService;
import vn.vietroute.business.catalog.service.BookingSourceCatalogService;
import vn.vietroute.business.report.dto.request.ReportSearchRequest;
import vn.vietroute.business.report.service.ReportService;
import vn.vietroute.business.tracking.dto.request.TrackingStartRequest;
import vn.vietroute.business.tracking.service.DriverTrackingService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.database.DatabaseInitializer;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcTransactions;
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

    @Test
    void bulkRoleAssignmentCannotTradeListForDetailOrExpandLegacyProjectionAndKeepsNativeEmptyDeny() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var delegate = member(scope, "BULKDELEGATE", "'ACCESS_ASSIGN','VEHICLE_READ'", "bulkdelegate");
        grantResources(
            "BULKDELEGATE",
            0,
            Set.of("PUT /api/v1/admin/access/users/{id}/roles", "GET /api/v1/admin/vehicles"),
            admin,
            200
        );
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES('BULKLIST','List','List'),('BULKDETAIL','Detail','Detail'),('BULKLEGACY','Legacy','Legacy'),('BULKEMPTY','Empty','Empty'); INSERT INTO auth_role_permissions(role_code,permission_code) SELECT code,'VEHICLE_READ' FROM auth_roles WHERE code IN('BULKLIST','BULKDETAIL','BULKLEGACY','BULKEMPTY'); INSERT INTO auth_role_resource_sets(role_code) VALUES('BULKLIST'),('BULKDETAIL'),('BULKEMPTY'); INSERT INTO auth_role_resources(role_code,kind,target) VALUES('BULKLIST','ENDPOINT','GET /api/v1/admin/vehicles'),('BULKDETAIL','ENDPOINT','GET /api/v1/admin/vehicles/{id}')"
        );
        long target = Long.parseLong(
            sql(
                "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email,email_verified_at) SELECT 'bulkreceiver',password_hash,'Receiver',false,'bulkreceiver@example.invalid',CURRENT_TIMESTAMP FROM auth_users WHERE id=1 RETURNING id"
            )
        );
        String path = "/api/v1/admin/access/users/" + target + "/roles";
        call(200, "PUT", path, "{\"roles\":[\"BULKLIST\"],\"version\":0}", delegate);
        call(403, "PUT", path, "{\"roles\":[\"BULKDETAIL\"],\"version\":1}", delegate);
        call(403, "PUT", path, "{\"roles\":[\"BULKLEGACY\"],\"version\":1}", delegate);
        assertEquals(
            "1:BULKLIST",
            sql(
                "SELECT u.version||':'||ur.role_code FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id WHERE u.id=" +
                    target
            )
        );
        call(200, "PUT", path, "{\"roles\":[\"BULKLIST\",\"BULKEMPTY\"],\"version\":1}", delegate);
        assertEquals("2", sql("SELECT count(*) FROM auth_user_roles WHERE user_id=" + target));
        sql("INSERT INTO auth_role_permissions(role_code,permission_code) VALUES('BULKEMPTY','ACCESS_DELETE')");
        call(403, "PUT", path, "{\"roles\":[\"BULKLIST\"],\"version\":2}", delegate);
        assertEquals("2", sql("SELECT version FROM auth_users WHERE id=" + target));
    }

    @Test
    void roleGrantSnapshotAndMembershipHistoryStayBoundedAndBatchFailureRollsBackAllEntries() throws Exception {
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) SELECT 'BULKROLE'||n,'Test','Test' FROM generate_series(1,32) n; INSERT INTO auth_role_permissions(role_code,permission_code) SELECT code,'ACCESS_READ' FROM auth_roles WHERE code LIKE 'BULKROLE%'; INSERT INTO auth_role_resource_sets(role_code) VALUES('BULKROLE1'); UPDATE auth_permissions SET active=false WHERE code='ACCESS_READ'"
        );
        var selected = new LinkedHashSet<Role>();
        for (int index = 1; index <= 32; index++) selected.add(Role.valueOf("BULKROLE" + index));
        var statements = new AtomicInteger();
        var grants = root
            .getBean(JdbcTransactions.class)
            .execute(connection ->
                root.getBean(RoleResourceRepository.class).grants(counted(connection, statements), selected)
            );
        assertEquals(1, statements.get());
        assertEquals(32, grants.size());
        assertTrue(grants.stream().allMatch(g -> g.permissions().contains(Permission.ACCESS_READ)));
        assertTrue(
            grants
                .stream()
                .filter(g -> g.role().name().equals("BULKROLE1"))
                .allMatch(g -> g.initialized() && g.targets().isEmpty())
        );
        assertTrue(grants.stream().anyMatch(g -> !g.initialized()));
        var entries = new ArrayList<AccessChangeRepository.Entry>();
        for (int index = 1; index <= 32; index++) entries.add(
            new AccessChangeRepository.Entry(
                Event.SCOPE_MEMBERSHIP,
                Target.MEMBERSHIP,
                "BULKHISTORY" + index,
                AccessChangeStateResponse.empty(),
                AccessChangeStateResponse.empty()
            )
        );
        var history = root.getBean(AccessChangeRepository.class);
        statements.set(0);
        root.getBean(JdbcTransactions.class).execute(connection -> {
            history.appendAll(counted(connection, statements), entries, 1, null);
            return null;
        });
        assertEquals(3, statements.get(), "Một batch, một advisory cleanup lock và một cleanup; không 3 x 32");
        assertEquals("32", sql("SELECT count(*) FROM auth_access_changes WHERE target_key LIKE 'BULKHISTORY%'"));
        sql(
            "DELETE FROM auth_access_changes WHERE target_key LIKE 'BULKHISTORY%'; CREATE FUNCTION reject_bulk_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.target_key=''BULKHISTORY17'' THEN RAISE EXCEPTION ''bulk fixture failure''; END IF; RETURN NEW; END'; CREATE TRIGGER reject_bulk_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION reject_bulk_history()"
        );
        assertThrows(BusinessException.class, () ->
            root.getBean(JdbcTransactions.class).execute(connection -> {
                history.appendAll(connection, entries, 1, null);
                return null;
            })
        );
        assertEquals("0", sql("SELECT count(*) FROM auth_access_changes WHERE target_key LIKE 'BULKHISTORY%'"));
    }

    /** Đếm execute thực tế (batch là một lượt JDBC), không chỉ prepareStatement để bỏ sót execute trong for. */
    private Connection counted(Connection connection, AtomicInteger statements) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                try {
                    Object value = method.invoke(connection, args);
                    if (
                        method.getName().equals("prepareStatement") && value instanceof PreparedStatement query
                    ) return Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] { PreparedStatement.class },
                        (prepared, action, parameters) -> {
                            if (action.getName().startsWith("execute")) statements.incrementAndGet();
                            try {
                                return action.invoke(query, parameters);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                        }
                    );
                    return value;
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        );
    }

    @Test
    void permissionValidationUsesOneQueryForWholeSetAndRejectsMissingInactiveOrDeletedCodes() throws Exception {
        var repository = root.getBean(AccessRepository.class);
        var permissions = new LinkedHashSet<Permission>();
        try (
            var connection = root.getBean(JdbcConnectionFactory.class).open();
            var query = connection.prepareStatement("SELECT code FROM auth_permissions WHERE active")
        ) {
            try (var rows = query.executeQuery()) {
                while (rows.next()) permissions.add(Permission.valueOf(rows.getString(1)));
            }
        }
        var statements = new AtomicInteger();
        root.getBean(JdbcTransactions.class).execute(connection -> {
            repository.validateMappings(
                counted(connection, statements),
                Set.of(Role.ADMIN, Role.DRIVER, Role.CUSTOMER),
                permissions
            );
            return null;
        });
        assertTrue(permissions.size() > 50);
        assertEquals(1, statements.get());
        for (String change : new String[] {
            "UPDATE auth_roles SET active=false WHERE code='DRIVER'",
            "UPDATE auth_roles SET active=false,deleted=true WHERE code='DRIVER'",
            "UPDATE auth_permissions SET active=false WHERE code='ACCESS_READ'",
        }) {
            sql(change);
            assertEquals(
                ErrorCode.INVALID_REQUEST,
                assertThrows(BusinessException.class, () ->
                    root.getBean(JdbcTransactions.class).execute(connection -> {
                        repository.validateMappings(connection, Set.of(Role.DRIVER), Set.of(Permission.ACCESS_READ));
                        return null;
                    })
                ).getErrorCode()
            );
            sql("UPDATE auth_roles SET active=true,deleted=false WHERE code='DRIVER'");
        }
        assertEquals(
            ErrorCode.INVALID_REQUEST,
            assertThrows(BusinessException.class, () ->
                root.getBean(JdbcTransactions.class).execute(connection -> {
                    repository.validateMappings(connection, Set.of(Role.valueOf("MISSINGROLE")), Set.of());
                    return null;
                })
            ).getErrorCode()
        );
    }

    @Test
    void tripCancellationSqlCountDoesNotGrowWithOneThousandPendingBookingsAndKeepsEveryHistoryAndVersion()
        throws Exception {
        seed();
        var fixture = tripFixture(false);
        var lifecycle = root.getBean(BookingLifecycleRepository.class);
        for (int size : new int[] { 1, 1000 }) {
            long trip = openBookingTrip(
                fixture,
                "BULK" + size,
                Instant.now().plus(size == 1 ? 10 : 20, ChronoUnit.DAYS)
            );
            long base = call(
                201,
                "POST",
                "/api/v1/admin/bookings",
                bookingBody(trip, 1, 3, 1, UUID.randomUUID()),
                admin
            )
                .path("id")
                .asLong();
            sql(
                "INSERT INTO bookings(trip_id,carrier_id,scope_id,source_code,source_id,pickup_stop_id,dropoff_stop_id,pickup_order,dropoff_order,seats,unit_price,total_price,contact_name,contact_phone,request_key,status,paid_amount) SELECT b.trip_id,b.carrier_id,b.scope_id,b.source_code,b.source_id,b.pickup_stop_id,b.dropoff_stop_id,b.pickup_order,b.dropoff_order,1,b.unit_price,b.unit_price,'Bulk fixture','0000000000',gen_random_uuid(),CASE WHEN n<=1 THEN 'CONFIRMED' ELSE 'PENDING' END,CASE WHEN n<=1 THEN 100 ELSE 0 END FROM bookings b CROSS JOIN generate_series(1," +
                    size +
                    ") n WHERE b.id=" +
                    base
            );
            sql("UPDATE trip_segments SET reserved_seats=1,version=1 WHERE trip_id=" + trip);
            if (size == 1000) {
                sql(
                    "CREATE FUNCTION suppress_bulk_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.event_type=''CANCELLED'' AND NEW.booking_id=" +
                        base +
                        " THEN RETURN NULL; END IF; RETURN NEW; END'; CREATE TRIGGER suppress_bulk_event BEFORE INSERT ON booking_events FOR EACH ROW EXECUTE FUNCTION suppress_bulk_event()"
                );
                var failure = assertThrows(BusinessException.class, () ->
                    root.getBean(JdbcTransactions.class).execute(connection -> {
                        try (
                            var query = connection.prepareStatement(
                                "SELECT id FROM carrier_trips WHERE id=? FOR UPDATE"
                            )
                        ) {
                            query.setLong(1, trip);
                            try (var rows = query.executeQuery()) {
                                rows.next();
                            }
                        }
                        lifecycle.cancelTrip(connection, trip, "Suppressed event", 1);
                        return null;
                    })
                );
                assertEquals(ErrorCode.STALE_VERSION, failure.getErrorCode());
                assertEquals(
                    "0",
                    sql("SELECT count(*) FROM bookings WHERE trip_id=" + trip + " AND status='CANCELLED'")
                );
                assertEquals(
                    "2:2",
                    sql("SELECT sum(reserved_seats)||':'||sum(version) FROM trip_segments WHERE trip_id=" + trip)
                );
                sql("DROP TRIGGER suppress_bulk_event ON booking_events; DROP FUNCTION suppress_bulk_event()");
            }
            var statements = new AtomicInteger();
            root.getBean(JdbcTransactions.class).execute(connection -> {
                try (var query = connection.prepareStatement("SELECT id FROM carrier_trips WHERE id=? FOR UPDATE")) {
                    query.setLong(1, trip);
                    try (var rows = query.executeQuery()) {
                        rows.next();
                    }
                }
                lifecycle.cancelTrip(counted(connection, statements), trip, "Bulk cancel", 1);
                return null;
            });
            assertEquals(5, statements.get(), "Số lượt SQL không tăng theo số đơn");
            assertEquals(
                Integer.toString(size + 1),
                sql(
                    "SELECT count(*) FROM bookings WHERE trip_id=" +
                        trip +
                        " AND status='CANCELLED' AND version=1 AND updated_by=1"
                )
            );
            assertEquals(
                Integer.toString(size + 1),
                sql(
                    "SELECT count(*) FROM booking_events WHERE trip_id=" +
                        trip +
                        " AND event_type='CANCELLED' AND note='Bulk cancel' AND created_by=1 AND amount=0 AND from_status IN('PENDING','CONFIRMED')"
                )
            );
            assertEquals(
                "0:4",
                sql("SELECT sum(reserved_seats)||':'||sum(version) FROM trip_segments WHERE trip_id=" + trip)
            );
            assertEquals(
                "100.00:0.00",
                sql("SELECT sum(paid_amount)||':'||sum(refunded_amount) FROM bookings WHERE trip_id=" + trip)
            );
            root.getBean(JdbcTransactions.class).execute(connection -> {
                lifecycle.cancelTrip(connection, trip, "Repeat", 1);
                return null;
            });
            assertEquals(
                Integer.toString(size + 1),
                sql("SELECT count(*) FROM booking_events WHERE trip_id=" + trip + " AND event_type='CANCELLED'")
            );
        }
    }

    @Test
    void loginUsesTwoBusinessStatementsAndReturnsCommittedAuditVersionAndCurrentRoles() throws Exception {
        sql(
            "UPDATE auth_users SET failed_login_attempts=3,locked_until=CURRENT_TIMESTAMP-interval '1 minute' WHERE id=1"
        );
        long before = Long.parseLong(sql("SELECT version FROM auth_users WHERE id=1"));
        String tokenVersion = sql("SELECT token_version FROM auth_users WHERE id=1");
        var statements = new AtomicInteger();
        var transactions = new JdbcTransactions(root.getBean(JdbcConnectionFactory.class)) {
            @Override
            public <T> T execute(SqlWork<T> work) {
                return super.execute(connection ->
                    work.apply(
                        (Connection) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { Connection.class },
                            (proxy, method, args) -> {
                                if (method.getName().equals("prepareStatement")) statements.incrementAndGet();
                                try {
                                    return method.invoke(connection, args);
                                } catch (InvocationTargetException exception) {
                                    throw exception.getCause();
                                }
                            }
                        )
                    )
                );
            }
        };
        var auth = new AuthService(
            root.getBean(UserRepository.class),
            transactions,
            root.getBean(PasswordEncoder.class),
            root.getBean(JwtService.class),
            Clock.systemUTC()
        );
        var login = new LoginRequest();
        login.setUsername("TESTADMIN");
        login.setPassword("TestOnly!2026Aa");
        var response = auth.login(login);
        assertEquals(2, statements.get(), "Chỉ SELECT FOR UPDATE và UPDATE RETURNING; schema/commit vẫn giữ nguyên");
        assertEquals(before + 1, response.user().getVersion());
        assertTrue(response.user().getRoles().contains(Role.ADMIN));
        assertEquals(
            "0:true:0",
            sql(
                "SELECT failed_login_attempts || ':' || (locked_until IS NULL) || ':' || updated_by FROM auth_users WHERE id=1"
            )
        );
        assertEquals(tokenVersion, sql("SELECT token_version FROM auth_users WHERE id=1"));
        assertFalse(child.getBean(ObjectMapper.class).writeValueAsString(response.user()).contains("passwordHash"));
        assertEquals(before + 1, Long.parseLong(sql("SELECT version FROM auth_users WHERE id=1")));
    }

    @Test
    void loginStillCommitsFiveFailuresLocksAndRollsBackAnUnsuccessfulReset() throws Exception {
        var auth = root.getBean(AuthService.class);
        var login = new LoginRequest();
        login.setUsername("testadmin");
        login.setPassword("WrongOnly!2026Aa");
        for (int attempt = 1; attempt <= 5; attempt++) {
            var error = assertThrows(BusinessException.class, () -> auth.login(login));
            assertEquals(attempt == 5 ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        }
        login.setPassword("TestOnly!2026Aa");
        assertEquals(
            ErrorCode.ACCOUNT_LOCKED,
            assertThrows(BusinessException.class, () -> auth.login(login)).getErrorCode()
        );
        assertEquals(
            "5:true",
            sql("SELECT failed_login_attempts || ':' || (locked_until>CURRENT_TIMESTAMP) FROM auth_users WHERE id=1")
        );
        sql("UPDATE auth_users SET locked_until=CURRENT_TIMESTAMP-interval '1 minute' WHERE id=1");
        long before = Long.parseLong(sql("SELECT version FROM auth_users WHERE id=1"));
        sql(
            "CREATE FUNCTION reject_login_reset() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.failed_login_attempts=0 THEN RAISE EXCEPTION ''test reset failure''; END IF; RETURN NEW; END'; CREATE TRIGGER reject_login_reset BEFORE UPDATE ON auth_users FOR EACH ROW EXECUTE FUNCTION reject_login_reset()"
        );
        assertThrows(BusinessException.class, () -> auth.login(login));
        assertEquals("5:" + before, sql("SELECT failed_login_attempts || ':' || version FROM auth_users WHERE id=1"));
    }

    @Test
    void successfulLoginTimingKeepsCsrfProtectionAndIsAbsentForInvalidCredentials() throws Exception {
        String body = "{\"username\":\"testadmin\",\"password\":\"TestOnly!2026Aa\"}";
        var denied = mvc
            .perform(request(HttpMethod.POST, "/api/v1/auth/login").contentType("application/json").content(body))
            .andReturn()
            .getResponse();
        assertEquals(403, denied.getStatus());
        assertEquals(null, denied.getHeader("Server-Timing"));
        var success = mvc
            .perform(
                request(HttpMethod.POST, "/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body)
            )
            .andReturn()
            .getResponse();
        assertEquals(200, success.getStatus());
        assertTrue(success.getHeader("Server-Timing").matches("auth;dur=[0-9]+"));
        var invalid = mvc
            .perform(
                request(HttpMethod.POST, "/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body.replace("TestOnly!2026Aa", "WrongOnly!2026Aa"))
            )
            .andReturn()
            .getResponse();
        assertEquals(401, invalid.getStatus());
        assertEquals(null, invalid.getHeader("Server-Timing"));
    }

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
    void trackingRequiresAssignedDriverAndConfirmedOwnerWithoutPublicCoordinates() throws Exception {
        seed();
        var fixture = tripFixture(false);
        var other = tripFixture(true);
        long trip = openBookingTrip(fixture, "TRACKPRIVATE", Instant.now().plus(5, ChronoUnit.MINUTES));
        long foreign = openBookingTrip(other, "TRACKOTHER", Instant.now().plus(5, ChronoUnit.MINUTES));
        var driver = tripDriver(fixture);
        var otherDriver = tripDriver(other);
        var buyer = customer("trackbuyer");
        var stranger = customer("trackstranger");
        long booking = call(201, "POST", "/api/v1/my-bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), buyer)
            .path("id")
            .asLong();
        String view = "/api/v1/my-bookings/" + booking + "/tracking";
        assertEquals("UNAVAILABLE", call(200, "GET", view, null, buyer).path("status").asText());
        call(404, "GET", view, null, stranger);
        call(403, "GET", view, null, admin);
        call(404, "POST", "/api/v1/driver/trips/" + trip + "/tracking/start", "{\"tripVersion\":1}", otherDriver);
        call(404, "POST", "/api/v1/driver/trips/" + trip + "/tracking/start", "{\"tripVersion\":1}", admin);
        var nonce = call(200, "POST", "/api/v1/driver/trips/" + trip + "/tracking/start", "{\"tripVersion\":1}", driver)
            .path("sessionKey")
            .asText();
        assertEquals(
            "WAITING",
            call(200, "GET", "/api/v1/driver/trips/" + trip + "/tracking", null, driver)
                .path("status")
                .asText()
        );
        call(
            200,
            "POST",
            "/api/v1/driver/trips/" + trip + "/tracking/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
        assertTrue(call(200, "GET", view, null, buyer).path("latest").isNull());
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + booking + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"\"}",
            admin
        );
        var shown = call(200, "GET", view, null, buyer);
        assertEquals("LIVE", shown.path("status").asText());
        sql("DELETE FROM auth_role_permissions WHERE role_code='DRIVER' AND permission_code='DRIVER_TRIP_UPDATE'");
        assertEquals("OFF", call(200, "GET", view, null, buyer).path("status").asText());
        long driverUser = ((UserPrincipal) driver.getPrincipal()).id();
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES('TRACKWRITER','Bổ sung','Extra'); INSERT INTO auth_user_roles(user_id,role_code) VALUES(" +
                driverUser +
                ",'TRACKWRITER'); INSERT INTO auth_role_permissions(role_code,permission_code) VALUES('TRACKWRITER','DRIVER_TRIP_UPDATE'); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                fixture.scope() +
                "," +
                driverUser +
                ",'TRACKWRITER')"
        );
        assertEquals("LIVE", call(200, "GET", view, null, buyer).path("status").asText());
        sql(
            "DELETE FROM auth_scope_roles WHERE scope_id=" +
                fixture.scope() +
                " AND user_id=" +
                driverUser +
                " AND role_code='TRACKWRITER'"
        );
        sql(
            "INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES(" +
                other.scope() +
                "," +
                driverUser +
                "); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                other.scope() +
                "," +
                driverUser +
                ",'TRACKWRITER')"
        );
        assertEquals("OFF", call(200, "GET", view, null, buyer).path("status").asText());
        sql("INSERT INTO auth_role_permissions(role_code,permission_code) VALUES('DRIVER','DRIVER_TRIP_UPDATE')");
        assertEquals("UNCONFIGURED", shown.path("mapStatus").asText());
        assertEquals(10.123456, shown.path("latest").path("latitude").asDouble());
        assertFalse(shown.has("sessionKey"));
        assertFalse(shown.has("driverUserId"));
        var staff = member(fixture.scope(), "TRACKREADER", "'TRIP_READ'", "trackreader");
        assertEquals(
            "LIVE",
            call(200, "GET", "/api/v1/admin/trips/" + trip + "/tracking", null, staff)
                .path("status")
                .asText()
        );
        call(404, "GET", "/api/v1/admin/trips/" + foreign + "/tracking", null, staff);
        assertEquals(
            401,
            mvc
                .perform(request(HttpMethod.GET, "/api/v1/admin/trips/" + trip + "/tracking"))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        assertEquals(
            404,
            mvc
                .perform(request(HttpMethod.GET, "/api/v1/trips/" + trip + "/tracking"))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        call(
            200,
            "PUT",
            "/api/v1/my-bookings/" + booking + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy TEST\"}",
            buyer
        );
        assertEquals("UNAVAILABLE", call(200, "GET", view, null, buyer).path("status").asText());
    }

    private String trackingBody(String nonce, Instant measured, String latitude) {
        return (
            "{\"sessionKey\":\"" +
            nonce +
            "\",\"latitude\":" +
            latitude +
            ",\"longitude\":106.123456,\"accuracy\":12.5,\"measuredAt\":\"" +
            measured +
            "\"}"
        );
    }

    @Test
    void trackingRejectsOldFutureOutOfOrderAndFastSamplesAndReplayDoesNotRenewLease() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "TRACKSAMPLES", Instant.now().plus(5, ChronoUnit.MINUTES));
        var driver = tripDriver(fixture);
        String path = "/api/v1/driver/trips/" + trip + "/tracking";
        call(409, "POST", path + "/start", "{\"tripVersion\":0}", driver);
        String nonce = call(200, "POST", path + "/start", "{\"tripVersion\":1}", driver)
            .path("sessionKey")
            .asText();
        Instant measured = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String sample = trackingBody(nonce, measured, "10.123456");
        var first = call(200, "POST", path + "/samples", sample, driver);
        var replay = call(200, "POST", path + "/samples", sample, driver);
        assertEquals(first.path("receivedAt"), replay.path("receivedAt"));
        assertEquals("1", sql("SELECT count(*) FROM trip_locations WHERE trip_id=" + trip));
        call(400, "POST", path + "/samples", trackingBody(nonce, measured.minusSeconds(200), "10.123456"), driver);
        call(400, "POST", path + "/samples", trackingBody(nonce, measured.plusSeconds(60), "10.123456"), driver);
        call(400, "POST", path + "/samples", trackingBody(nonce, measured.minusMillis(1), "10.123456"), driver);
        call(400, "POST", path + "/samples", trackingBody(nonce, measured, "10.654321"), driver);
        call(400, "POST", path + "/samples", trackingBody(nonce, measured, "91"), driver);
        call(400, "POST", path + "/samples", trackingBody(nonce, measured, "10.1234567"), driver);
        call(429, "POST", path + "/samples", trackingBody(nonce, measured.plusMillis(1), "10.123456"), driver);
        sql("UPDATE trip_tracking SET received_at=CURRENT_TIMESTAMP-INTERVAL '11 seconds' WHERE trip_id=" + trip);
        call(200, "POST", path + "/samples", trackingBody(nonce, measured.plusMillis(2), "10.123456"), driver);
        assertEquals("2", sql("SELECT count(*) FROM trip_locations WHERE trip_id=" + trip));
        assertEquals("OPEN", sql("SELECT status FROM carrier_trips WHERE id=" + trip));
        assertEquals(
            "0",
            sql("SELECT count(*) FROM trip_stops WHERE trip_id=" + trip + " AND actual_departure_at IS NOT NULL")
        );
        sql("UPDATE trip_tracking SET received_at=CURRENT_TIMESTAMP-INTERVAL '121 seconds' WHERE trip_id=" + trip);
        var stale = call(200, "GET", path, null, driver);
        assertEquals("STALE", stale.path("status").asText());
        assertTrue(stale.path("latest").isNull());
        assertEquals(0, stale.path("trail").size());
        call(
            409,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
        var fresh = call(200, "POST", path + "/start", "{\"tripVersion\":1}", driver)
            .path("sessionKey")
            .asText();
        assertFalse(fresh.equals(nonce));
        call(409, "POST", path + "/stop", "{\"sessionKey\":\"" + nonce + "\"}", driver);
        call(
            409,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
        for (int retry = 0; retry < 2; retry++) assertEquals(
            "OFF",
            call(200, "POST", path + "/stop", "{\"sessionKey\":\"" + fresh + "\"}", driver)
                .path("status")
                .asText()
        );
        assertEquals("true", sql("SELECT (latitude IS NULL)::text FROM trip_tracking WHERE trip_id=" + trip));
        sql("DELETE FROM auth_role_permissions WHERE role_code='DRIVER' AND permission_code='DRIVER_TRIP_UPDATE'");
        call(403, "POST", path + "/start", "{\"tripVersion\":1}", driver);
        SecurityContextHolder.getContext().setAuthentication(driver);
        assertThrows(AccessDeniedException.class, () ->
            root
                .getBean(DriverTrackingService.class)
                .start(trip, new TrackingStartRequest(1L), (UserPrincipal) driver.getPrincipal())
        );
    }

    @Test
    void trackingHistoryFailureRollsBackSnapshotAndRetentionIsBounded() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "TRACKROLLBACK", Instant.now().plus(5, ChronoUnit.MINUTES));
        var driver = tripDriver(fixture);
        String path = "/api/v1/driver/trips/" + trip + "/tracking";
        String nonce = call(200, "POST", path + "/start", "{\"tripVersion\":1}", driver)
            .path("sessionKey")
            .asText();
        sql(
            "CREATE FUNCTION fail_tracking_sample() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'sample fixture'; END $$; CREATE TRIGGER fail_tracking_sample BEFORE INSERT ON trip_locations FOR EACH ROW EXECUTE FUNCTION fail_tracking_sample()"
        );
        call(
            503,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
        assertEquals("true", sql("SELECT (latitude IS NULL)::text FROM trip_tracking WHERE trip_id=" + trip));
        sql(
            "DROP TRIGGER fail_tracking_sample ON trip_locations; DROP FUNCTION fail_tracking_sample(); INSERT INTO trip_locations(trip_id,carrier_id,scope_id,driver_user_id,session_key,source,latitude,longitude,accuracy,measured_at,received_at) SELECT " +
                trip +
                "," +
                fixture.carrier() +
                "," +
                fixture.scope() +
                ",(SELECT driver_user_id FROM carrier_trips WHERE id=" +
                trip +
                "),'" +
                nonce +
                "','BROWSER',10,106,10,CURRENT_TIMESTAMP-INTERVAL '8 days',CURRENT_TIMESTAMP-INTERVAL '8 days' FROM generate_series(1,2100)"
        );
        call(
            200,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
        assertTrue(Long.parseLong(sql("SELECT count(*) FROM trip_locations WHERE trip_id=" + trip)) <= 2000);
        assertEquals(1, call(200, "GET", path, null, driver).path("trail").size());
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + trip + "/state",
            "{\"status\":\"CANCELLED\",\"version\":1,\"note\":\"Hủy TEST\"}",
            admin
        );
        assertEquals(
            "false:true",
            sql("SELECT sharing||':'||(latitude IS NULL) FROM trip_tracking WHERE trip_id=" + trip)
        );
        assertEquals("CLOSED", call(200, "GET", path, null, driver).path("status").asText());
        call(
            409,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
    }

    @Test
    void trackingSameSampleConcurrentIsSingleAndCompletionRevokesConsent() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = openBookingTrip(fixture, "TRACKCOMPLETE", Instant.now().plus(5, ChronoUnit.MINUTES));
        var driver = tripDriver(fixture);
        String path = "/api/v1/driver/trips/" + trip + "/tracking";
        String nonce = call(200, "POST", path + "/start", "{\"tripVersion\":1}", driver)
            .path("sessionKey")
            .asText();
        String body = trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456");
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                ready.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return call(200, "POST", path + "/samples", body, driver);
            });
            var second = pool.submit(() -> {
                ready.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return call(200, "POST", path + "/samples", body, driver);
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(
                first.get(20, TimeUnit.SECONDS).path("receivedAt"),
                second.get(20, TimeUnit.SECONDS).path("receivedAt")
            );
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertEquals("1", sql("SELECT count(*) FROM trip_locations WHERE trip_id=" + trip));
        long version = 1;
        for (String step : new String[] { "START:1", "ARRIVE:2", "DEPART:2", "ARRIVE:3", "COMPLETE:3" }) {
            var parts = step.split(":");
            call(
                200,
                "PUT",
                "/api/v1/driver/trips/" + trip + "/progress",
                "{\"action\":\"" +
                    parts[0] +
                    "\",\"stopOrder\":" +
                    parts[1] +
                    ",\"version\":" +
                    version++ +
                    ",\"note\":\"TEST\"}",
                driver
            );
        }
        assertEquals(
            "false:true",
            sql("SELECT sharing||':'||(latitude IS NULL) FROM trip_tracking WHERE trip_id=" + trip)
        );
        assertEquals("CLOSED", call(200, "GET", path, null, driver).path("status").asText());
        call(
            409,
            "POST",
            path + "/samples",
            trackingBody(nonce, Instant.now().truncatedTo(ChronoUnit.MILLIS), "10.123456"),
            driver
        );
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
    void driverProvisioningCreatesOnlyPendingDriverScopeLinkAndAtomicHistory() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM"),
            driver = id("carrier_drivers", "DEMODRIVER02");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var staff = member(scope, "PROVISIONER", "'DRIVER_VIEW','DRIVER_READ','DRIVER_ASSIGN'", "provisioner");
        String body =
            "{\"username\":\"NewDriver\",\"email\":\"NEWDRIVER@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}";
        var result = call(201, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        long user = result.path("userId").asLong();
        assertEquals("newdriver", result.path("username").asText());
        assertTrue(result.path("mustChangePassword").asBoolean());
        assertTrue(result.path("emailVerificationRequired").asBoolean());
        assertFalse(result.path("driverActive").asBoolean());
        assertFalse(result.has("password"));
        assertFalse(result.has("email"));
        assertTrue(
            root
                .getBean(PasswordEncoder.class)
                .matches("TestOnly!2026Aa", sql("SELECT password_hash FROM auth_users WHERE id=" + user))
        );
        assertEquals(
            "true:true:false:true",
            sql(
                "SELECT active||':'||must_change_password||':'||activation_pending||':'||(email_verified_at IS NULL) FROM auth_users WHERE id=" +
                    user
            )
        );
        assertEquals("newdriver@example.invalid", sql("SELECT email FROM auth_users WHERE id=" + user));
        assertEquals("DRIVER", sql("SELECT string_agg(role_code,',') FROM auth_user_roles WHERE user_id=" + user));
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_scope_roles WHERE scope_id=" +
                    scope +
                    " AND user_id=" +
                    user +
                    " AND role_code='DRIVER'"
            )
        );
        assertEquals(
            user + ":false:1",
            sql("SELECT user_id||':'||active||':'||version FROM carrier_drivers WHERE id=" + driver)
        );
        assertEquals(
            "2",
            sql(
                "SELECT count(*) FROM auth_access_changes WHERE actor_id=" +
                    ((UserPrincipal) staff.getPrincipal()).id() +
                    " AND target_key IN('" +
                    user +
                    "','" +
                    scope +
                    ":" +
                    user +
                    "')"
            )
        );
        assertEquals("0", sql("SELECT count(*) FROM auth_account_challenges"));
        var pending = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(user, "newdriver", Set.of(Role.DRIVER), 1),
            null,
            Set.of()
        );
        call(403, "GET", "/api/v1/driver/trips", null, pending);
        call(403, "GET", "/api/v1/admin/accounts", null, staff);
        call(409, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        assertEquals("1", sql("SELECT count(*) FROM auth_users WHERE username='newdriver'"));
        call(
            400,
            "PUT",
            "/api/v1/admin/drivers/" + driver,
            "{\"name\":\"Tài xế mới\",\"phone\":\"0000000000\",\"active\":true,\"userId\":" + user + ",\"version\":1}",
            admin
        );
    }

    @Test
    void driverProvisioningCannotCrossScopeEscalateRoleOrUseRevokedAssignment() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE code='DEMOMIENNAM'"));
        long driver = id("carrier_drivers", "DEMODRIVER02"),
            foreign = id("carrier_drivers", "DEMODRIVER03");
        var staff = member(scope, "SCOPEDPROVISION", "'DRIVER_READ','DRIVER_ASSIGN'", "scopedprovision");
        String body =
            "{\"username\":\"scopeaccount\",\"email\":\"scopeaccount@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}";
        assertTrue(
            call(200, "GET", "/api/v1/admin/drivers/" + driver, null, staff)
                .path("canAssign")
                .asBoolean()
        );
        call(404, "POST", "/api/v1/admin/drivers/" + foreign + "/account", body, staff);
        call(
            409,
            "POST",
            "/api/v1/admin/drivers/" + driver + "/account",
            body.replace("scopeaccount\"", "testadmin\""),
            staff
        );
        call(
            400,
            "POST",
            "/api/v1/admin/drivers/" + driver + "/account",
            body.replace("TestOnly!2026Aa", "weak"),
            staff
        );
        call(
            409,
            "POST",
            "/api/v1/admin/drivers/" + driver + "/account",
            body.replace("version\":0", "version\":9"),
            staff
        );
        sql("INSERT INTO auth_role_permissions(role_code,permission_code) VALUES('DRIVER','ACCOUNT_READ')");
        call(409, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        assertEquals("0", sql("SELECT count(*) FROM auth_users WHERE username='scopeaccount'"));
        sql(
            "DELETE FROM auth_role_permissions WHERE role_code='DRIVER' AND permission_code='ACCOUNT_READ'; DELETE FROM auth_role_permissions WHERE role_code='SCOPEDPROVISION' AND permission_code='DRIVER_ASSIGN'"
        );
        assertFalse(
            call(200, "GET", "/api/v1/admin/drivers/" + driver, null, staff)
                .path("canAssign")
                .asBoolean()
        );
        call(403, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        SecurityContextHolder.getContext().setAuthentication(staff);
        assertThrows(AccessDeniedException.class, () ->
            root
                .getBean(DriverService.class)
                .provision(driver, new DriverAccountCreateRequest(), (UserPrincipal) staff.getPrincipal())
        );
    }

    @Test
    void driverProvisioningHistoryFailureRollsBackCredentialMembershipAndProfile() throws Exception {
        seed();
        long driver = id("carrier_drivers", "DEMODRIVER02");
        String users = sql("SELECT count(*) FROM auth_users"),
            memberships = sql("SELECT count(*) FROM auth_scope_memberships");
        sql(
            "CREATE FUNCTION fail_driver_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'history fixture'; END $$; CREATE TRIGGER fail_driver_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_driver_history()"
        );
        call(
            503,
            "POST",
            "/api/v1/admin/drivers/" + driver + "/account",
            "{\"username\":\"rollbackdriver\",\"email\":\"rollbackdriver@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}",
            admin
        );
        assertEquals(users, sql("SELECT count(*) FROM auth_users"));
        assertEquals(memberships, sql("SELECT count(*) FROM auth_scope_memberships"));
        assertEquals(
            "true:true:0",
            sql("SELECT (user_id IS NULL)||':'||active||':'||version FROM carrier_drivers WHERE id=" + driver)
        );
    }

    @Test
    void concurrentDriverProvisioningHasOneAccountAndAssignedTripBlocksIdentityReplacement() throws Exception {
        seed();
        long driver = id("carrier_drivers", "DEMODRIVER02");
        String originalCount = sql("SELECT count(*) FROM auth_users");
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                ready.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return mvc
                    .perform(
                        request(HttpMethod.POST, "/api/v1/admin/drivers/" + driver + "/account")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(
                                "{\"username\":\"racedrivera\",\"email\":\"racedrivera@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}"
                            )
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus();
            });
            var second = pool.submit(() -> {
                ready.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return mvc
                    .perform(
                        request(HttpMethod.POST, "/api/v1/admin/drivers/" + driver + "/account")
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(
                                "{\"username\":\"racedriverb\",\"email\":\"racedriverb@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}"
                            )
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus();
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(Set.of(201, 409), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertEquals(Long.parseLong(originalCount) + 1, Long.parseLong(sql("SELECT count(*) FROM auth_users")));
        assertEquals("1", sql("SELECT count(*) FROM auth_users WHERE username IN('racedrivera','racedriverb')"));
        var fixture = tripFixture(false);
        openBookingTrip(fixture, "BLOCKDRIVERLINK", Instant.now().plus(2, ChronoUnit.DAYS));
        call(
            409,
            "PUT",
            "/api/v1/admin/drivers/" + fixture.driver(),
            "{\"name\":\"Không đổi account\",\"phone\":\"0000000000\",\"active\":true,\"userId\":null,\"version\":0}",
            admin
        );
        assertFalse(sql("SELECT user_id FROM carrier_drivers WHERE id=" + fixture.driver()).isEmpty());
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
    void publicCarrierLookupHasIndependentProjectionPagingAndPublicationState() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM");
        long other = id("carriers", "DEMOBACNAM");
        var response = mvc.perform(request(HttpMethod.GET, "/api/v1/carriers?size=1")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var page = json.readTree(response.getContentAsString()).path("data");
        assertEquals(2, page.path("totalElements").asInt());
        assertEquals(1, page.path("content").size());
        for (String field : Set.of(
            "scopeId",
            "createdBy",
            "updatedBy",
            "version",
            "logoMediaId",
            "canUpdate",
            "userId"
        ))
            assertFalse(page.at("/content/0").has(field));
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var operator = member(scope, "PUBLICLOOKUP", "'VEHICLE_READ'", "publicviewer");
        assertEquals(2, call(200, "GET", "/api/v1/carriers", null, operator).path("totalElements").asInt());
        call(403, "GET", "/api/v1/admin/carriers", null, operator);
        call(200, "GET", "/carriers?lang=vi", null, operator);
        call(200, "GET", "/carriers?lang=en", null, operator);
        assertEquals(0, call(200, "GET", "/api/v1/carriers?query=%25_%27", null, admin).path("totalElements").asInt());
        sql("UPDATE carriers SET active=false WHERE id=" + other);
        call(404, "GET", "/api/v1/carriers/" + other, null, admin);
        assertEquals(1, call(200, "GET", "/api/v1/carriers", null, admin).path("totalElements").asInt());
        sql("UPDATE auth_data_scopes SET active=false WHERE id=" + scope);
        call(404, "GET", "/api/v1/carriers/" + carrier, null, admin);
        assertEquals(0, call(200, "GET", "/api/v1/carriers", null, admin).path("totalElements").asInt());
    }

    @Test
    void publicCarrierLogoReturnsOnlyReadyPublishedUrlWithoutOpeningMediaMetadata() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM");
        long image = Long.parseLong(
            sql(
                "INSERT INTO media_assets(public_id,status,url,thumbnail_url,format,bytes,width,height,created_by,updated_by) VALUES ('public-logo-test','READY','https://example.invalid/logo.jpg','https://example.invalid/logo-thumb.jpg','jpg',1024,32,32,1,1) RETURNING id"
            )
        );
        sql("UPDATE carriers SET logo_media_id=" + image + " WHERE id=" + carrier);
        var response = mvc
            .perform(request(HttpMethod.GET, "/api/v1/carriers/" + carrier))
            .andReturn()
            .getResponse();
        assertEquals(200, response.getStatus());
        var detail = json.readTree(response.getContentAsString()).path("data");
        assertEquals("https://example.invalid/logo.jpg", detail.path("logoUrl").asText());
        assertFalse(detail.has("logoMediaId"));
        assertFalse(detail.toString().contains("public-logo-test"));
        assertEquals(
            401,
            mvc
                .perform(request(HttpMethod.GET, "/api/v1/media/" + image))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        sql("UPDATE media_assets SET status='DELETING' WHERE id=" + image);
        assertTrue(
            call(200, "GET", "/api/v1/carriers/" + carrier, null, admin)
                .path("logoUrl")
                .isNull()
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

    private JsonNode grantResources(String role, long version, Set<String> targets, Authentication who, int status)
        throws Exception {
        return call(
            status,
            "PUT",
            "/api/v1/admin/access/roles/" + role + "/resources",
            json.writeValueAsString(Map.of("targets", targets, "version", version)),
            who
        );
    }

    @Test
    void upgradeV021PreservesExistingMappingsAndNeverEnrollsNewEndpointsOnRestart() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var user = member(scope, "UPGRADENATIVE", "'VEHICLE_READ','VEHICLE_VIEW','VEHICLE_CREATE'", "upgradenative");
        String hash = sql("SELECT md5(password_hash) FROM auth_users WHERE id=1");
        sql(
            "DROP TABLE auth_role_resources; DROP TABLE auth_role_resource_sets; ALTER TABLE auth_users DROP CONSTRAINT ck_auth_users_deleted; ALTER TABLE auth_users DROP COLUMN deleted; DELETE FROM vietroute_schema_history WHERE version='V022__role_resource_grants.sql'"
        );
        root.getBean(DatabaseInitializer.class).afterPropertiesSet();
        assertEquals("22", sql("SELECT count(*) FROM vietroute_schema_history"));
        assertEquals(hash, sql("SELECT md5(password_hash) FROM auth_users WHERE id=1"));
        assertEquals("0", sql("SELECT permission_version FROM auth_roles WHERE code='UPGRADENATIVE'"));
        assertEquals("3", sql("SELECT count(*) FROM auth_role_permissions WHERE role_code='UPGRADENATIVE'"));
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_role_resources WHERE role_code='UPGRADENATIVE' AND target='GET /api/v1/admin/vehicles/{id}'"
            )
        );
        call(200, "GET", "/api/v1/admin/vehicles", null, user);
        sql(
            "INSERT INTO auth_access_resources(kind,target,handler,description,public_route,configurable,declared_permissions,function_code) VALUES('ENDPOINT','GET /future-native-api','Fixture#future','Future',false,true,'VEHICLE_READ','VEHICLE')"
        );
        var metadata = child.getBean(AccessResourceSynchronizer.class).inventory();
        root.getBean(AccessResourceService.class).synchronize(metadata, Set.of());
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM auth_role_resources WHERE role_code='UPGRADENATIVE' AND target='GET /future-native-api'"
            )
        );
        assertEquals("0", sql("SELECT permission_version FROM auth_roles WHERE code='UPGRADENATIVE'"));
        var current = call(200, "GET", "/api/v1/admin/access/roles/UPGRADENATIVE/resources", null, admin);
        Set<String> targets = new LinkedHashSet<>();
        current.path("targets").forEach(item -> targets.add(item.asText()));
        assertTrue(targets.contains("POST /api/v1/admin/demo"));
        grantResources("UPGRADENATIVE", 0, targets, admin, 200);
        assertEquals("3", sql("SELECT count(*) FROM auth_role_permissions WHERE role_code='UPGRADENATIVE'"));
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM auth_role_permissions WHERE role_code='UPGRADENATIVE' AND permission_code IN ('DEMO_CREATE','PROVINCE_CREATE','ACCESS_ASSIGN')"
            )
        );
    }

    @Test
    void nativeResourcesSeparateListDetailMenuAndRevocationAcrossWarContexts() throws Exception {
        seed();
        long carrier = id("carriers", "DEMOMIENNAM");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var user = member(scope, "NATIVEREADER", "'VEHICLE_READ','VEHICLE_VIEW'", "nativereader");
        String page = "GET /operations/vehicles",
            list = "GET /api/v1/admin/vehicles",
            detail = list + "/{id}";
        grantResources("NATIVEREADER", 0, Set.of(page, list), admin, 200);
        assertEquals(
            "0",
            sql(
                "SELECT jsonb_array_length(after_state->'roles') FROM auth_access_changes WHERE event_type='ROLE_RESOURCES' AND target_key='NATIVEREADER' ORDER BY id DESC LIMIT 1"
            )
        );
        assertEquals(2, call(200, "GET", "/api/v1/admin/vehicles", null, user).path("totalElements").asInt());
        call(200, "GET", "/operations/vehicles", null, user);
        long vehicle = id("carrier_vehicles", "DEMOVEH01");
        call(403, "GET", "/api/v1/admin/vehicles/" + vehicle, null, user);
        call(403, "GET", "/api/v1/admin/vehicles/carriers", null, user);
        call(403, "GET", "/operations/routes", null, user);
        grantResources("NATIVEREADER", 0, Set.of(detail), admin, 409);
        grantResources("NATIVEREADER", 1, Set.of("GET /api/v1/carriers"), admin, 400);
        grantResources("NATIVEREADER", 1, Set.of("GET /api/v1/auth/me"), admin, 400);
        grantResources("NATIVEREADER", 1, Set.of(page, detail), admin, 200);
        call(403, "GET", "/api/v1/admin/vehicles", null, user);
        call(200, "GET", "/api/v1/admin/vehicles/" + vehicle, null, user);
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_role_permissions WHERE role_code='NATIVEREADER' AND permission_code='VEHICLE_READ'"
            )
        );
        var preview = call(
            200,
            "GET",
            "/api/v1/admin/accounts/" + ((UserPrincipal) user.getPrincipal()).id() + "/access",
            null,
            admin
        );
        assertFalse(preview.path("capabilities").toString().contains("\"GET /api/v1/admin/vehicles\""));
        assertTrue(preview.path("capabilities").toString().contains(detail));
        grantResources("NATIVEREADER", 2, Set.of(), admin, 200);
        call(403, "GET", "/operations/vehicles", null, user);
        call(403, "GET", "/api/v1/admin/vehicles/" + vehicle, null, user);
        call(200, "GET", "/api/v1/auth/capabilities", null, user);
        assertEquals("0", sql("SELECT count(*) FROM auth_role_resources WHERE role_code='NATIVEREADER'"));
    }

    @Test
    void nativeScopeCannotBorrowDetailEndpointFromRoleOutsideThatScope() throws Exception {
        seed();
        long first = id("carriers", "DEMOMIENNAM"),
            second = id("carriers", "DEMOBACNAM");
        long scopeA = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + first));
        long scopeB = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + second));
        var user = member(scopeA, "SCOPEDLIST", "'VEHICLE_READ'", "scopedlist");
        long userId = ((UserPrincipal) user.getPrincipal()).id();
        grantResources("SCOPEDLIST", 0, Set.of("GET /api/v1/admin/vehicles"), admin, 200);
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/SCOPEDDETAIL",
            "{\"nameVi\":\"Chi tiết\",\"nameEn\":\"Detail\",\"active\":true,\"version\":0}",
            admin
        );
        grantResources("SCOPEDDETAIL", 0, Set.of("GET /api/v1/admin/vehicles/{id}"), admin, 200);
        sql(
            "INSERT INTO auth_user_roles(user_id,role_code) VALUES(" +
                userId +
                ",'SCOPEDDETAIL'); " +
                "INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                scopeA +
                "," +
                userId +
                ",'SCOPEDDETAIL'); " +
                "INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES(" +
                scopeB +
                "," +
                userId +
                "); " +
                "INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                scopeB +
                "," +
                userId +
                ",'SCOPEDLIST')"
        );
        assertEquals(4, call(200, "GET", "/api/v1/admin/vehicles", null, user).path("totalElements").asInt());
        call(200, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "DEMOVEH01"), null, user);
        call(404, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "DEMOVEH03"), null, user);
    }

    @Test
    void compatibilityAssignmentsAndRoleDeletionRetainExactResourceHistory() throws Exception {
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/RESOURCEHISTORY",
            "{\"nameVi\":\"Lịch sử\",\"nameEn\":\"History\",\"active\":true,\"version\":0}",
            admin
        );
        grantResources("RESOURCEHISTORY", 0, Set.of("GET /api/v1/admin/vehicles"), admin, 200);
        call(
            200,
            "PUT",
            "/api/v1/admin/access/roles/RESOURCEHISTORY/permissions",
            "{\"permissions\":[\"VEHICLE_READ\"],\"version\":1}",
            admin
        );
        assertEquals(
            "2",
            sql(
                "SELECT count(*) FROM auth_access_changes WHERE event_type='ROLE_RESOURCES' AND target_key='RESOURCEHISTORY'"
            )
        );
        assertEquals(
            "true",
            sql(
                "SELECT (after_state->'resources' @> '[\"GET /api/v1/admin/vehicles/{id}\"]'::jsonb)::text FROM auth_access_changes WHERE event_type='ROLE_RESOURCES' AND target_key='RESOURCEHISTORY' ORDER BY id DESC LIMIT 1"
            )
        );
        call(200, "DELETE", "/api/v1/admin/access/registry/roles/RESOURCEHISTORY?version=0", null, admin);
        assertEquals(
            "true",
            sql(
                "SELECT (before_state->'resources' @> '[\"GET /api/v1/admin/vehicles/{id}\"]'::jsonb AND after_state->'resources'='[]'::jsonb)::text FROM auth_access_changes WHERE event_type='ROLE_DELETED' AND target_key='RESOURCEHISTORY'"
            )
        );
        assertEquals("0", sql("SELECT count(*) FROM auth_role_resource_sets WHERE role_code='RESOURCEHISTORY'"));
    }

    @Test
    void nativeDelegationAndHistoryFailureCannotEscalateOrPartiallyCommit() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var delegate = member(scope, "NATIVEDELEGATE", "'ACCESS_ASSIGN','VEHICLE_READ'", "nativedelegate");
        grantResources(
            "NATIVEDELEGATE",
            0,
            Set.of("PUT /api/v1/admin/access/roles/{role}/resources", "GET /api/v1/admin/vehicles"),
            admin,
            200
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/NATIVETARGET",
            "{\"nameVi\":\"Đích\",\"nameEn\":\"Target\",\"active\":true,\"version\":0}",
            admin
        );
        grantResources("NATIVETARGET", 0, Set.of("GET /api/v1/admin/vehicles/{id}"), delegate, 403);
        grantResources("NATIVETARGET", 0, Set.of("GET /api/v1/admin/vehicles"), delegate, 200);
        grantResources("ADMIN", 0, Set.of(), admin, 403);
        sql(
            "CREATE FUNCTION fail_resource_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test history failure''; END'; CREATE TRIGGER fail_resource_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_resource_history()"
        );
        grantResources("NATIVETARGET", 1, Set.of(), admin, 503);
        assertEquals("1", sql("SELECT permission_version FROM auth_roles WHERE code='NATIVETARGET'"));
        assertEquals("1", sql("SELECT count(*) FROM auth_role_resources WHERE role_code='NATIVETARGET'"));
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_role_permissions WHERE role_code='NATIVETARGET' AND permission_code='VEHICLE_READ'"
            )
        );
    }

    @Test
    void managedAccountEditAndSoftDeletePreserveIdentityAndRevokeAccess() throws Exception {
        seed();
        var user = customer("softdeleteuser");
        long userId = ((UserPrincipal) user.getPrincipal()).id();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        sql(
            "INSERT INTO auth_scope_memberships(scope_id,user_id) VALUES(" +
                scope +
                "," +
                userId +
                "); INSERT INTO auth_scope_roles(scope_id,user_id,role_code) VALUES(" +
                scope +
                "," +
                userId +
                ",'CUSTOMER')"
        );
        var edited = call(
            200,
            "PUT",
            "/api/v1/admin/accounts/" + userId,
            "{\"displayName\":\"Tên mới\",\"version\":0}",
            admin
        );
        assertEquals("Tên mới", edited.path("displayName").asText());
        call(409, "DELETE", "/api/v1/admin/accounts/" + userId + "?version=0", null, admin);
        call(403, "DELETE", "/api/v1/admin/accounts/1?version=0", null, admin);
        call(200, "DELETE", "/api/v1/admin/accounts/" + userId + "?version=1", null, admin);
        assertEquals(
            "true:false:1",
            sql("SELECT deleted||':'||active||':'||token_version FROM auth_users WHERE id=" + userId)
        );
        assertEquals("0", sql("SELECT count(*) FROM auth_user_roles WHERE user_id=" + userId));
        assertEquals("0", sql("SELECT count(*) FROM auth_scope_memberships WHERE user_id=" + userId));
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_access_changes WHERE event_type='SCOPE_MEMBERSHIP' AND target_key='" +
                    scope +
                    ":" +
                    userId +
                    "' AND before_state->'roles' @> '[\"CUSTOMER\"]'::jsonb AND after_state->'roles'='[]'::jsonb"
            )
        );
        assertEquals(
            0,
            call(200, "GET", "/api/v1/admin/accounts?username=softdeleteuser", null, admin)
                .path("totalElements")
                .asInt()
        );
        call(403, "GET", "/api/v1/my-bookings", null, user);
        call(404, "GET", "/api/v1/admin/accounts/" + userId + "/access", null, admin);
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_access_changes WHERE event_type='ACCOUNT_DELETED' AND target_key='" +
                    userId +
                    "' AND after_state->>'deleted'='true'"
            )
        );
    }

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
        assignFixtureResources("DRIVER");
        long user = Long.parseLong(sql("SELECT user_id FROM carrier_drivers WHERE id=" + fixture.driver()));
        return actor(user, sql("SELECT username FROM auth_users WHERE id=" + user), "DRIVER");
    }

    private Authentication customer(String username) throws Exception {
        sql(
            "INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'CUSTOMER',code FROM auth_permissions WHERE code IN('MY_BOOKING_VIEW','MY_BOOKING_READ','MY_BOOKING_CREATE','MY_BOOKING_UPDATE') ON CONFLICT DO NOTHING"
        );
        assignFixtureResources("CUSTOMER");
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

    /** Fixture native gán chính xác API đã biết sau khi thêm projection; không bỏ marker để né guard mới. */
    private void assignFixtureResources(String role) throws Exception {
        sql(
            "INSERT INTO auth_role_resources(role_code,kind,target) SELECT '" +
                role +
                "',kind,target FROM auth_access_resources r WHERE NOT r.public_route AND EXISTS(SELECT 1 FROM auth_role_permissions p WHERE p.role_code='" +
                role +
                "' AND p.permission_code=ANY(string_to_array(r.declared_permissions,','))) ON CONFLICT DO NOTHING"
        );
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
        var apiDocument = mvc.perform(request(HttpMethod.GET, "/v3/api-docs")).andReturn().getResponse();
        assertEquals(200, apiDocument.getStatus());
        var parameters = json.readTree(apiDocument.getContentAsString()).at("/paths/~1api~1v1~1trips/get/parameters");
        assertTrue(
            parameters
                .findValuesAsText("name")
                .containsAll(
                    Set.of(
                        "fromLocationId",
                        "fromProvinceId",
                        "toLocationId",
                        "toProvinceId",
                        "date",
                        "seats",
                        "carrierId",
                        "vehicleTypeId",
                        "minPrice",
                        "maxPrice",
                        "page",
                        "size"
                    )
                )
        );
        assertEquals(
            1,
            call(200, "GET", path + "&minPrice=150000&maxPrice=150000&carrierId=" + fixture.carrier(), null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(200, "GET", path + "&maxPrice=149999.99", null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(200, "GET", path + "&minPrice=150000.01", null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(200, "GET", path + "&carrierId=" + id("carriers", "DEMOBACNAM"), null, admin)
                .path("totalElements")
                .asInt()
        );
        long type = Long.parseLong(sql("SELECT vehicle_type_id FROM carrier_vehicles WHERE id=" + fixture.vehicle()));
        assertEquals(
            1,
            call(200, "GET", path + "&vehicleTypeId=" + type, null, admin)
                .path("totalElements")
                .asInt()
        );
        long otherType = Long.parseLong(
            sql("SELECT min(id) FROM catalog_items WHERE type='VEHICLE_TYPE' AND id<>" + type)
        );
        assertEquals(
            0,
            call(200, "GET", path + "&vehicleTypeId=" + otherType, null, admin)
                .path("totalElements")
                .asInt()
        );
        var priceError = mvc
            .perform(request(HttpMethod.GET, path + "&minPrice=2&maxPrice=1"))
            .andReturn()
            .getResponse();
        assertEquals(400, priceError.getStatus());
        assertEquals("VAL015", json.readTree(priceError.getContentAsString()).at("/errors/0/code").asText());
        call(400, "GET", path + "&minPrice=-1", null, admin);
        call(400, "GET", path + "&maxPrice=1.001", null, admin);
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
