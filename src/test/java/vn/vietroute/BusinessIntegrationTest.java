package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
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
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.FunctionSearch;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.auth.entity.AccountChallenge;
import vn.vietroute.auth.entity.Function;
import vn.vietroute.auth.entity.FunctionResource;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.RoleFunction;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.entity.UserRole;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.AccountChallengeRepo;
import vn.vietroute.auth.repo.FunctionRepo;
import vn.vietroute.auth.repo.FunctionResourceRepo;
import vn.vietroute.auth.repo.RoleFunctionRepo;
import vn.vietroute.auth.repo.RoleRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.auth.service.UserService;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.bootstrap.initializer.BusinessDataInitializer;
import vn.vietroute.business.bootstrap.repo.BusinessDataRepo;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.carrier.service.DriverService;
import vn.vietroute.business.carrier.service.VehicleService;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.repo.CategoryRepo;
import vn.vietroute.business.catalog.service.CategoryService;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.report.repo.ReportRepo;
import vn.vietroute.business.report.service.ReportService;
import vn.vietroute.common.api_journal.dto.response.ApiJournalDetail;
import vn.vietroute.common.api_journal.dto.response.ApiJournalEntry;
import vn.vietroute.common.api_journal.dto.search.ApiJournalSearch;
import vn.vietroute.common.api_journal.repo.ApiJournalRepo;
import vn.vietroute.common.base.BaseRepository.SqlWork;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.initializer.DatabaseInitializer;
import vn.vietroute.common.initializer.MigrationCatalog;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.support.LegacyBusinessGrants;
import vn.vietroute.support.PooledReadProbe;
import vn.vietroute.support.SqlPlanProbe;

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
    void searchIndexMigrationUsesExistingExtensionNamespaceWithoutChangingSearchPathOrMappings() throws Exception {
        String extensionSchema = "perf03_ext_" + UUID.randomUUID().toString().replace("-", "");
        String mappings = sql(
            "SELECT count(*) FROM (SELECT DISTINCT rf.role_code,op AS permission_code FROM auth_role_functions rf CROSS JOIN LATERAL unnest(rf.operation_codes) op)"
        );
        sql("CREATE SCHEMA " + extensionSchema + "; ALTER EXTENSION pg_trgm SET SCHEMA " + extensionSchema);
        try {
            // Index hiện hành giữ đúng OID opclass khi extension được chuyển namespace.
            root.getBean(DatabaseInitializer.class).afterPropertiesSet();
            assertEquals(
                extensionSchema,
                sql(
                    "SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='pg_trgm'"
                )
            );
            assertEquals(
                "3",
                sql(
                    "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace cn ON cn.oid=c.relnamespace JOIN pg_opclass o ON o.oid=ANY(i.indclass) JOIN pg_namespace ns ON ns.oid=o.opcnamespace WHERE cn.nspname=current_schema() AND c.relname IN('idx_carriers_search_contact','idx_carriers_search_name','idx_category_search_text') AND ns.nspname='" +
                        extensionSchema +
                        "'"
                )
            );
            assertEquals(
                mappings,
                sql(
                    "SELECT count(*) FROM (SELECT DISTINCT rf.role_code,op AS permission_code FROM auth_role_functions rf CROSS JOIN LATERAL unnest(rf.operation_codes) op)"
                )
            );
            assertEquals("10", sql("SELECT count(*) FROM vietroute_schema_history"));
            try (
                var connection = root.getBean(JdbcConnectionFactory.class).open();
                var query = connection.prepareStatement("SHOW search_path");
                var row = query.executeQuery()
            ) {
                assertTrue(row.next());
                assertFalse(row.getString(1).contains(extensionSchema));
                assertFalse(row.getString(1).contains("public"));
            }
        } finally {
            sql("ALTER EXTENSION pg_trgm SET SCHEMA " + schema + "; DROP SCHEMA " + extensionSchema);
        }
    }

    @Test
    void searchIndexesPreserveLiteralWildcardsVietnameseAndScope() throws Exception {
        seed();
        sql("UPDATE carriers SET name='Nhà xe đà nẵng 50%_!\\ literal' WHERE code='VRMIENNAM'");
        for (String term : new String[] { "đà nẵng", "%_!\\", "vrmiennam Nhà" }) {
            var search = new PublicCarrierSearch();
            search.setQuery(term);
            assertEquals(
                1,
                root
                    .getBean(JdbcTransactions.class)
                    .execute(connection -> root.getBean(CarrierRepo.class).page(connection, search))
                    .getTotalElements()
            );
            var report = new ReportCarrierSearch();
            report.setQuery(term);
            approve(admin, "GET /api/v1/admin/reports/carriers");
            assertEquals(
                1,
                root
                    .getBean(JdbcTransactions.class)
                    .execute(connection -> root.getBean(ReportRepo.class).carriers(connection, report))
                    .getTotalElements()
            );
        }
        var search = new PublicCarrierSearch();
        search.setQuery("%");
        sql(
            "UPDATE carriers SET scope_active=false WHERE scope_id=(SELECT scope_id FROM carriers WHERE code='VRMIENNAM')"
        );
        assertEquals(
            0,
            root
                .getBean(JdbcTransactions.class)
                .execute(connection -> root.getBean(CarrierRepo.class).page(connection, search))
                .getTotalElements()
        );
    }

    @Test
    void performancePlansUseMeasuredSearchAndReportIndexesOnRepresentativeFixture() throws Exception {
        seed();
        var first = tripFixture(false);
        var second = tripFixture(true);
        long trip = openBookingTrip(first, "PERFBASE", Instant.now().plus(10, ChronoUnit.DAYS));
        long other = openBookingTrip(second, "PERFOTHER", Instant.now().plus(10, ChronoUnit.DAYS));
        long booking = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        try (var input = getClass().getResourceAsStream("/fixtures/performance.sql")) {
            sql(
                new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(":otherTrip", Long.toString(other))
                    .replace(":trip", Long.toString(trip))
                    .replace(":booking", Long.toString(booking))
            );
        }
        assertEquals("30000", sql("SELECT count(*) FROM carrier_trips WHERE code LIKE 'PERF03T%'"));
        assertEquals(
            "90000",
            sql("SELECT count(*) FROM bookings b JOIN carrier_trips t ON t.id=b.trip_id WHERE t.code LIKE 'PERF03T%'")
        );
        approve(admin, "GET /api/v1/admin/reports/carriers");
        try (var connection = root.getBean(JdbcConnectionFactory.class).open()) {
            var reports = root.getBean(ReportRepo.class);
            var publicSearch = new PublicCarrierSearch();
            publicSearch.setQuery("needlexyz");
            var picker = new ReportCarrierSearch();
            picker.setQuery("needlexyz");
            var catalog = new CategorySearch();
            catalog.setQuery("needlexyz");
            var report = new ReportSearch();
            report.setFromDate(LocalDate.of(2027, 2, 10));
            report.setToDate(report.getFromDate());
            Map<String, List<SqlPlanProbe.Select>> queries = new LinkedHashMap<>();
            queries.put("report.all", SqlPlanProbe.capture(connection, observed -> reports.summary(observed, report)));
            report.setCarrierId(first.carrier());
            queries.put(
                "report.carrier",
                SqlPlanProbe.capture(connection, observed -> reports.summary(observed, report))
            );
            queries.put(
                "search.public",
                SqlPlanProbe.capture(connection, observed ->
                    root.getBean(CarrierRepo.class).page(observed, publicSearch)
                )
            );
            queries.put(
                "search.reportPicker",
                SqlPlanProbe.capture(connection, observed -> reports.carriers(observed, picker))
            );
            queries.put(
                "search.catalog",
                SqlPlanProbe.capture(connection, observed ->
                    root.getBean(CategoryRepo.class).search(observed, catalog, "VEHICLE_TYPE", "VALUE", false)
                )
            );
            Map<String, List<JsonNode>> baseline = new LinkedHashMap<>();
            var point = connection.setSavepoint();
            try (var query = connection.createStatement()) {
                query.execute(
                    "DROP INDEX idx_trips_report_departure,idx_trips_report_carrier_date,idx_carriers_search_contact,idx_carriers_search_name,idx_category_search_text"
                );
            }
            for (var entry : queries.entrySet()) {
                var plans = new ArrayList<JsonNode>();
                for (var query : entry.getValue()) plans.add(query.explain(connection));
                baseline.put(entry.getKey(), plans);
            }
            connection.rollback(point);
            try (var query = connection.createStatement()) {
                query.execute("ANALYZE carriers; ANALYZE category; ANALYZE carrier_trips");
            }
            var used = new LinkedHashSet<String>();
            for (var entry : queries.entrySet()) {
                int index = 0;
                for (var query : entry.getValue()) {
                    var before = baseline.get(entry.getKey()).get(index++);
                    var after = query.explain(connection);
                    used.addAll(SqlPlanProbe.indexes(after));
                    Files.writeString(
                        Path.of("target", "perf03-plan-" + entry.getKey() + index + ".json"),
                        after.toPrettyString()
                    );
                    System.out.println(
                        "PERF03_PLAN " +
                            json.writeValueAsString(
                                Map.of(
                                    "query",
                                    entry.getKey() + index,
                                    "before",
                                    SqlPlanProbe.summary(before),
                                    "after",
                                    SqlPlanProbe.summary(after)
                                )
                            )
                    );
                    assertTrue(
                        SqlPlanProbe.indexes(after)
                            .stream()
                            .anyMatch(name -> name.contains("_search_") || name.startsWith("idx_trips_report_")),
                        "Planner phải chọn index mới cho fixture chọn lọc"
                    );
                }
            }
            assertTrue(
                used.containsAll(
                    Set.of("idx_carriers_search_contact", "idx_carriers_search_name", "idx_category_search_text")
                )
            );
        }
        var report = new ReportSearch();
        report.setFromDate(LocalDate.of(2027, 2, 1));
        report.setToDate(LocalDate.of(2027, 2, 28));
        var search = new PublicCarrierSearch();
        search.setQuery("needlexyz");
        PooledReadProbe.run(
            System.getProperty("business.test.database"),
            System.getProperty("business.test.user", "ciadmin"),
            schema,
            admin,
            connection -> {
                assertTrue(root.getBean(ReportRepo.class).summary(connection, report).getTripCount() > 0);
                assertEquals(20, root.getBean(CarrierRepo.class).page(connection, search).getTotalElements());
                return null;
            }
        );
    }

    @Test
    void largeCancellationAggregatesOverlappingConfirmedSegmentsAndMeasuresLockHold() throws Exception {
        seed();
        var fixture = tripFixture(false);
        long trip = call(
            201,
            "POST",
            "/api/v1/admin/trips",
            tripBody(fixture, "PERFCANCEL", Instant.now().plus(10, ChronoUnit.DAYS)).replace(
                "\"capacity\":10",
                "\"capacity\":29"
            ),
            admin
        )
            .path("id")
            .asLong();
        call(
            200,
            "PUT",
            "/api/v1/admin/trips/" + trip + "/state",
            "{\"status\":\"OPEN\",\"version\":0,\"note\":\"\"}",
            admin
        );
        long base = call(201, "POST", "/api/v1/admin/bookings", bookingBody(trip, 1, 3, 1, UUID.randomUUID()), admin)
            .path("id")
            .asLong();
        sql(
            "INSERT INTO bookings(trip_id,carrier_id,scope_id,source_code,source_id,pickup_stop_id,dropoff_stop_id,pickup_order,dropoff_order,seats,unit_price,total_price,contact_name,contact_phone,request_key,status,paid_amount) SELECT b.trip_id,b.carrier_id,b.scope_id,'PHONE',b.source_id,p.id,d.id,p.stop_order,d.stop_order,CASE WHEN n BETWEEN 9 AND 24 THEN 2 ELSE 1 END,d.cumulative_fare-p.cumulative_fare,(d.cumulative_fare-p.cumulative_fare)*CASE WHEN n BETWEEN 9 AND 24 THEN 2 ELSE 1 END,'Bulk fixture','0000000000',gen_random_uuid(),CASE WHEN n<=24 THEN 'CONFIRMED' ELSE 'PENDING' END,CASE WHEN n<=24 THEN 100 ELSE 0 END FROM bookings b CROSS JOIN generate_series(1,5000) n JOIN trip_stops p ON p.trip_id=b.trip_id AND p.stop_order=CASE WHEN n BETWEEN 17 AND 24 THEN 2 ELSE 1 END JOIN trip_stops d ON d.trip_id=b.trip_id AND d.stop_order=CASE WHEN n BETWEEN 9 AND 16 THEN 2 ELSE 3 END WHERE b.id=" +
                base
        );
        sql("UPDATE trip_segments SET reserved_seats=24,version=16 WHERE trip_id=" + trip);
        var factory = root.getBean(JdbcConnectionFactory.class);
        var executor = Executors.newSingleThreadExecutor();
        var attempted = new CountDownLatch(1);
        var statements = new AtomicInteger();
        try (var blocker = factory.open()) {
            try (var query = blocker.prepareStatement("SELECT id FROM carrier_trips WHERE id=? FOR UPDATE")) {
                query.setLong(1, trip);
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                }
            }
            var cancellation = executor.submit(() -> {
                try (var connection = factory.open()) {
                    try {
                        long waiting = System.nanoTime();
                        attempted.countDown();
                        try (
                            var query = connection.prepareStatement(
                                "SELECT id FROM carrier_trips WHERE id=? FOR UPDATE"
                            )
                        ) {
                            query.setLong(1, trip);
                            try (var row = query.executeQuery()) {
                                assertTrue(row.next());
                            }
                        }
                        long acquired = System.nanoTime();
                        root.getBean(BookingRepo.class).cancelTrip(
                            counted(connection, statements),
                            trip,
                            "Load cancellation",
                            1
                        );
                        connection.commit();
                        return Map.of(
                            "controlledWaitMs",
                            (acquired - waiting) / 1_000_000d,
                            "lockHeldThroughCommitMs",
                            (System.nanoTime() - acquired) / 1_000_000d,
                            "bookings",
                            5001,
                            "confirmed",
                            24,
                            "jdbcExecutes",
                            statements.get()
                        );
                    } catch (Exception | Error failure) {
                        connection.rollback();
                        throw failure;
                    }
                }
            });
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            // Tạo tranh khóa có kiểm soát; không lấy 100ms chặn chủ động làm latency xử lý nghiệp vụ.
            Thread.sleep(100);
            assertFalse(cancellation.isDone());
            blocker.commit();
            System.out.println("PERF03_CANCEL " + json.writeValueAsString(cancellation.get(15, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(5, statements.get());
        assertEquals(
            "5001",
            sql(
                "SELECT count(*) FROM bookings WHERE trip_id=" +
                    trip +
                    " AND status='CANCELLED' AND version=1 AND updated_by=1"
            )
        );
        assertEquals(
            "5001",
            sql(
                "SELECT count(*) FROM booking_events WHERE trip_id=" +
                    trip +
                    " AND event_type='CANCELLED' AND created_by=1"
            )
        );
        assertEquals(
            "0:64",
            sql("SELECT sum(reserved_seats)||':'||sum(version) FROM trip_segments WHERE trip_id=" + trip)
        );
        assertEquals(
            "2400.00:0.00",
            sql("SELECT sum(paid_amount)||':'||sum(refunded_amount) FROM bookings WHERE trip_id=" + trip)
        );
        root.getBean(JdbcTransactions.class).execute(connection -> {
            root.getBean(BookingRepo.class).cancelTrip(connection, trip, "Repeated", 1);
            return null;
        });
        assertEquals(
            "0:64",
            sql("SELECT sum(reserved_seats)||':'||sum(version) FROM trip_segments WHERE trip_id=" + trip)
        );
        assertEquals(
            "5001",
            sql("SELECT count(*) FROM booking_events WHERE trip_id=" + trip + " AND event_type='CANCELLED'")
        );
    }

    @Test
    void bulkRoleAssignmentCannotTradeListForDetailOrExpandLegacyProjectionAndKeepsNativeEmptyDeny() throws Exception {
        seed();
        var reader = member(
            Long.parseLong(sql("SELECT min(scope_id) FROM carriers")),
            "EXACTREADER",
            "'VEHICLE_READ'",
            "exactreader"
        );
        grantResources("EXACTREADER", 0, Set.of("GET /api/v1/admin/vehicles"), admin, 200);
        call(200, "GET", "/api/v1/admin/vehicles", null, reader);
        call(403, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "VRSOUTH01"), null, reader);
        call(403, "GET", "/operations/vehicles", null, reader);
        grantResources("EXACTREADER", 1, Set.of(), admin, 200);
        call(403, "GET", "/api/v1/admin/vehicles", null, reader);
    }

    @Test
    void roleGrantSnapshotAndMembershipHistoryStayBoundedAndBatchFailureRollsBackAllEntries() throws Exception {
        var statements = new AtomicInteger();
        var statistics = root.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean statisticsEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long prepared = statistics.getPrepareStatementCount();
        try {
            root.getBean(JdbcTransactions.class).execute(connection -> {
                var codes = List.of("VEHICLE_READ", "TRIP_READ", "ROUTE_READ");
                var bindings = root.getBean(FunctionResourceRepo.class).targetsFor(codes);
                assertEquals(3, bindings.size());
                return null;
            });
            assertEquals(1, statistics.getPrepareStatementCount() - prepared);
        } finally {
            statistics.setStatisticsEnabled(statisticsEnabled);
        }
        statements.set(0);
        var before = new ArrayList<RoleDTO>();
        for (long scope = 1; scope <= 5; scope++) {
            var value = new RoleDTO();
            value.setScopeId(scope);
            value.setRoles(Set.of("CUSTOMER"));
            before.add(value);
        }
        root.getBean(JdbcTransactions.class).execute(connection -> {
            root.getBean(AccessChangeRepo.class).appendMemberships(
                counted(connection, statements),
                7,
                before,
                List.of(),
                0
            );
            return null;
        });
        assertEquals(1, statements.get(), "Năm membership phải ghi đúng một executeBatch");
        assertEquals("5", sql("SELECT count(*) FROM auth_access_changes WHERE event_type='SCOPE_MEMBERSHIP'"));
        sql("DELETE FROM auth_access_changes");
        sql(
            "CREATE FUNCTION fail_batch_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''batch history failure''; END'; CREATE TRIGGER fail_batch_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_batch_history()"
        );
        assertThrows(BusinessException.class, () ->
            root.getBean(JdbcTransactions.class).execute(connection -> {
                root.getBean(AccessChangeRepo.class).append(
                    connection,
                    "USER_ROLES",
                    "USER",
                    "7",
                    0,
                    Map.of(),
                    Map.of()
                );
                return null;
            })
        );
        assertEquals("0", sql("SELECT count(*) FROM auth_access_changes"));
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
    void roleValidationUsesOneQueryForWholeSetAndRejectsMissingInactiveOrDeletedCodes() throws Exception {
        var repository = root.getBean(UserRoleRepo.class);
        var statements = new AtomicInteger();
        root.getBean(JdbcTransactions.class).execute(connection -> {
            repository.validateMappings(
                counted(connection, statements),
                Set.of("ADMIN", "DRIVER", "CUSTOMER"),
                Set.of()
            );
            return null;
        });
        assertEquals(1, statements.get());
        for (String code : List.of("DRIVER", "MISSINGROLE")) {
            sql("UPDATE auth_roles SET active=false WHERE code='DRIVER'");
            assertThrows(BusinessException.class, () ->
                root.getBean(JdbcTransactions.class).execute(connection -> {
                    repository.validateMappings(connection, Set.of(code), Set.of());
                    return null;
                })
            );
        }
    }

    @Test
    void tripCancellationSqlCountDoesNotGrowWithOneThousandPendingBookingsAndKeepsEveryHistoryAndVersion()
        throws Exception {
        seed();
        var fixture = tripFixture(false);
        BookingRepo bookings = root.getBean(BookingRepo.class);
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
                        bookings.cancelTrip(connection, trip, "Suppressed event", 1);
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
                bookings.cancelTrip(counted(connection, statements), trip, "Bulk cancel", 1);
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
                bookings.cancelTrip(connection, trip, "Repeat", 1);
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
        var auth = new UserService(
            root.getBean(UserRepo.class),
            root.getBean(UserRoleRepo.class),
            root.getBean(AccountChallengeRepo.class),
            root.getBean(AccessChangeRepo.class),
            root.getBean(PasswordEncoder.class),
            root.getBean(JwtService.class),
            transactions,
            root.getBean(AppSettings.class),
            root.getBean(AuthInitializer.class),
            Clock.systemUTC()
        );
        auth.initializePasswordTiming();
        var login = new UserRequest();
        login.setUsername("TESTADMIN");
        login.setPassword("TestOnly!2026Aa");
        var response = auth.login(login);
        assertEquals(2, statements.get(), "Chỉ SELECT FOR UPDATE và UPDATE RETURNING; schema/commit vẫn giữ nguyên");
        assertEquals(before + 1, response.getUser().getVersion());
        assertTrue(response.getUser().getRoles().contains("ADMIN"));
        assertEquals(
            "0:true:0",
            sql(
                "SELECT failed_login_attempts || ':' || (locked_until IS NULL) || ':' || updated_by FROM auth_users WHERE id=1"
            )
        );
        assertEquals(tokenVersion, sql("SELECT token_version FROM auth_users WHERE id=1"));
        assertFalse(child.getBean(ObjectMapper.class).writeValueAsString(response.getUser()).contains("passwordHash"));
        assertEquals(before + 1, Long.parseLong(sql("SELECT version FROM auth_users WHERE id=1")));
    }

    @Test
    void loginStillCommitsFiveFailuresLocksAndRollsBackAnUnsuccessfulReset() throws Exception {
        var auth = root.getBean(UserService.class);
        var login = new UserRequest();
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

    @Test
    void nativeQueryRepositoriesKeepConfiguredSchemaPagingAndTransactionRollback() throws Exception {
        sql(
            "INSERT INTO auth_functions(code,name_vi,name_en,active) VALUES('QUERYONE','Trạm %_!','Native marker',false)"
        );
        var repository = root.getBean(FunctionRepo.class);
        var search = new FunctionSearch();
        search.setQuery("%_!");
        search.setActive(false);
        search.setSize(1);
        var transactions = root.getBean(JdbcTransactions.class);
        transactions.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL search_path TO pg_catalog,pg_temp");
            }
            var page = repository.search(search);
            assertEquals(1, page.getTotalElements());
            assertEquals("QUERYONE", page.getContent().get(0).getCode());
            assertEquals(1, root.getBean(FunctionResourceRepo.class).targetsFor(List.of("VEHICLE_READ")).size());
            return null;
        });
        var resources = root.getBean(FunctionResourceRepo.class);
        var mappings = root.getBean(RoleFunctionRepo.class);
        Set<String> before = transactions.execute(connection -> mappings.functions("CUSTOMER"));
        assertThrows(IllegalStateException.class, () ->
            transactions.execute(connection -> {
                var function = repository.lock("QUERYONE");
                function.setDescription("Rollback marker");
                repository.saveAndFlush(function);
                var resource = new FunctionDTO();
                resource.setKind("ENDPOINT");
                resource.setTarget("GET /api/v1/admin/functions");
                resource.setHttpMethod("GET");
                resource.setPathTemplate("/api/v1/admin/functions");
                resource.setHandler("Query fixture");
                resource.setDescription("Query fixture");
                resources.replace(connection, "QUERYONE", List.of(resource), 0);
                assertEquals(Set.of(resource.getTarget()), resources.targets("QUERYONE"));
                mappings.replace(connection, "CUSTOMER", Set.of("QUERYONE"), 0);
                assertEquals(Set.of("QUERYONE"), mappings.functions("CUSTOMER"));
                assertEquals(Set.of(), resources.targetsFor(List.of()).keySet());
                assertEquals(Set.of(), mappings.functionTargets(Set.of()));
                throw new IllegalStateException("Rollback fixture");
            })
        );
        assertEquals("", sql("SELECT description FROM auth_functions WHERE code='QUERYONE'"));
        assertEquals("0", sql("SELECT count(*) FROM auth_function_resources WHERE function_code='QUERYONE'"));
        assertEquals(before, transactions.execute(connection -> mappings.functions("CUSTOMER")));
    }

    @Test
    void eightTableFunctionCrudAssignmentsRevocationAndHistoryRollbackUseRealWarGuards() throws Exception {
        seed();
        assertEquals(
            "8",
            sql(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE' AND table_name LIKE 'auth\\_%' ESCAPE '\\'"
            )
        );
        var empty = member(
            Long.parseLong(sql("SELECT min(scope_id) FROM carriers")),
            "FUNCTIONUSER",
            "'VEHICLE_READ'",
            "functionuser"
        );
        long user = ((UserPrincipal) empty.getPrincipal()).id();
        String create =
            "{\"code\":\"VEHICLE_LIST\",\"nameVi\":\"Xem xe\",\"nameEn\":\"View vehicles\",\"active\":true,\"targets\":[\"GET /api/v1/admin/vehicles\"]}";
        call(403, "POST", "/api/v1/admin/access/functions", create, empty);
        var created = call(201, "POST", "/api/v1/admin/access/functions", create, admin);
        assertEquals("VEHICLE_LIST", created.path("code").asText());
        call(
            400,
            "POST",
            "/api/v1/admin/access/functions",
            create
                .replace("VEHICLE_LIST", "INVALID_TARGET")
                .replace("GET /api/v1/admin/vehicles", "GET /invented/route"),
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONUSER/functions",
            "{\"functionCodes\":[\"VEHICLE_LIST\"],\"version\":0}",
            admin
        );
        call(200, "GET", "/api/v1/admin/vehicles", null, empty);
        call(403, "GET", "/operations/vehicles", null, empty);
        call(403, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "VRSOUTH01"), null, empty);
        call(409, "DELETE", "/api/v1/admin/access/functions/VEHICLE_LIST?version=0", null, admin);
        call(
            409,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONUSER/functions",
            "{\"functionCodes\":[],\"version\":0}",
            admin
        );
        sql(
            "CREATE FUNCTION fail_function_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''history failure''; END'; CREATE TRIGGER fail_function_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_function_history()"
        );
        call(
            503,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONUSER/functions",
            "{\"functionCodes\":[],\"version\":1}",
            admin
        );
        assertEquals("1", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='FUNCTIONUSER'"));
        call(200, "GET", "/api/v1/admin/vehicles", null, empty);
        sql("DROP TRIGGER fail_function_history ON auth_access_changes; DROP FUNCTION fail_function_history()");
        call(
            200,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONUSER/functions",
            "{\"functionCodes\":[],\"version\":1}",
            admin
        );
        call(403, "GET", "/api/v1/admin/vehicles", null, empty);
        call(200, "DELETE", "/api/v1/admin/access/functions/VEHICLE_LIST?version=0", null, admin);
        call(404, "GET", "/api/v1/admin/access/functions/VEHICLE_LIST", null, admin);
        assertEquals("t", sql("SELECT deleted FROM auth_functions WHERE code='VEHICLE_LIST'"));
        assertEquals(
            "4",
            sql(
                "SELECT count(*) FROM auth_access_changes WHERE event_type IN('FUNCTION_CREATED','FUNCTION_DELETED','ROLE_FUNCTIONS') AND (target_key='VEHICLE_LIST' OR target_key='FUNCTIONUSER')"
            )
        );
        assertTrue(user > 1);
    }

    @Test
    void functionUpdateCasAndAdminTargetsCannotBeDelegatedOutsideActorAuthority() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var delegate = member(scope, "FUNCTIONDELEGATE", "'ACCESS_ASSIGN','VEHICLE_READ'", "functiondelegate");
        grantResources(
            "FUNCTIONDELEGATE",
            0,
            Set.of("PUT /api/v1/admin/access/roles/{role}/functions", "GET /api/v1/admin/vehicles"),
            admin,
            200
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/functions",
            "{\"code\":\"LIST_ONLY\",\"nameVi\":\"Danh sách\",\"nameEn\":\"List\",\"active\":true,\"targets\":[\"GET /api/v1/admin/vehicles\"]}",
            admin
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/functions",
            "{\"code\":\"DETAIL_ONLY\",\"nameVi\":\"Chi tiết\",\"nameEn\":\"Detail\",\"active\":true,\"targets\":[\"GET /api/v1/admin/vehicles/{id}\"]}",
            admin
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/FUNCTIONTARGET",
            "{\"nameVi\":\"Đích\",\"nameEn\":\"Target\",\"active\":true,\"version\":0}",
            admin
        );
        call(
            403,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONTARGET/functions",
            "{\"functionCodes\":[\"DETAIL_ONLY\"],\"version\":0}",
            delegate
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONTARGET/functions",
            "{\"functionCodes\":[\"LIST_ONLY\"],\"version\":0}",
            delegate
        );
        call(403, "PUT", "/api/v1/admin/access/roles/ADMIN/functions", "{\"functionCodes\":[],\"version\":0}", admin);
        String edit =
            "{\"code\":\"LIST_ONLY\",\"nameVi\":\"Danh sách mới\",\"nameEn\":\"Updated list\",\"active\":false,\"targets\":[\"GET /api/v1/admin/vehicles\"],\"version\":0}";
        call(200, "PUT", "/api/v1/admin/access/functions/LIST_ONLY", edit, admin);
        call(409, "PUT", "/api/v1/admin/access/functions/LIST_ONLY", edit, admin);
        assertEquals("2", sql("SELECT permission_version FROM auth_roles WHERE code='FUNCTIONTARGET'"));
        call(
            409,
            "PUT",
            "/api/v1/admin/access/roles/FUNCTIONTARGET/functions",
            "{\"functionCodes\":[],\"version\":1}",
            admin
        );
        var document = mvc
            .perform(request(HttpMethod.GET, "/v3/api-docs").with(authentication(admin)))
            .andReturn()
            .getResponse();
        assertEquals(200, document.getStatus());
        var spec = json.readTree(document.getContentAsString());
        var searchInputs = spec
            .path("paths")
            .path("/api/v1/admin/access/functions")
            .path("get")
            .path("parameters")
            .toString();
        for (String name : List.of("query", "active", "page", "size"))
            assertTrue(searchInputs.contains("\"name\":\"" + name + "\""));
        assertTrue(spec.path("components").path("schemas").has("FunctionRequest"));
        assertTrue(spec.path("components").path("schemas").has("FunctionRequest"));
    }

    @Test
    void defaultAccountsAreReadyScopeBoundAndIdempotentWithoutRepairingRevokedOrdinaryGrants() throws Exception {
        var envValues = Map.of(
            "VIETROUTE_DEFAULT_ACCOUNTS_ENABLED",
            "true",
            "VIETROUTE_DEFAULT_CUSTOMER_EMAIL",
            "customer@example.invalid",
            "VIETROUTE_DEFAULT_CUSTOMER_PASSWORD",
            "sample.customer",
            "VIETROUTE_DEFAULT_DRIVER_EMAIL",
            "driver@example.invalid",
            "VIETROUTE_DEFAULT_DRIVER_PASSWORD",
            "sample.driver",
            "VIETROUTE_DEFAULT_OPERATOR_EMAIL",
            "operator@example.invalid",
            "VIETROUTE_DEFAULT_OPERATOR_PASSWORD",
            "sample.operator"
        );
        var defaults = new AuthInitializer(
            root.getBean(UserRepo.class),
            root.getBean(RoleRepo.class),
            root.getBean(AppSettings.class),
            new ConfigurationEnvironment(envValues::get, null),
            root.getBean(PasswordEncoder.class),
            root.getBean(JdbcTransactions.class),
            json
        );
        var tx = root.getBean(JdbcTransactions.class);
        tx.execute(connection -> {
            defaults.initialize(connection);
            root.getBean(BusinessDataRepo.class).seed(connection, 0);
            root.getBean(BusinessDataRepo.class).seedRoutes(connection);
            root.getBean(UserRepo.class).assignInitialScopes(
                connection,
                root.getBean(AuthInitializer.class).getData().path("accounts")
            );
            root.getBean(AuthInitializer.class).synchronize(root.getBean(EndpointAccess.class).all());
            return null;
        });
        assertEquals(
            "3",
            sql(
                "SELECT count(*) FROM auth_users WHERE username IN('custom01','driver01','operator01') AND active AND NOT activation_pending AND NOT must_change_password AND email_verified_at IS NOT NULL"
            )
        );
        assertEquals("6", sql("SELECT count(*) FROM auth_user_roles WHERE scope_id>0"));
        assertEquals("2", sql("SELECT count(*) FROM carrier_routes"));
        assertEquals("6", sql("SELECT count(*) FROM carrier_route_stops"));
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM carrier_drivers d JOIN auth_users u ON u.id=d.user_id WHERE u.username='driver01'"
            )
        );
        SecurityContextHolder.getContext().setAuthentication(actor(1, "testadmin", "ADMIN"));
        var login = new UserRequest();
        login.setUsername("operator01");
        login.setPassword("sample.operator");
        var session = root.getBean(UserService.class).login(login);
        assertTrue(
            session
                .getUser()
                .getTargets()
                .stream()
                .anyMatch(target -> target.equals("GET /api/v1/admin/trips"))
        );
        String hash = sql("SELECT md5(password_hash) FROM auth_users WHERE username='driver01'");
        sql(
            "DELETE FROM auth_user_roles WHERE user_id=(SELECT id FROM auth_users WHERE username='driver01') AND scope_id>0; DELETE FROM auth_role_functions WHERE role_code='DRIVER'; UPDATE auth_users SET failed_login_attempts=3 WHERE username='driver01'"
        );
        tx.execute(connection -> {
            defaults.initialize(connection);
            root.getBean(BusinessDataRepo.class).seed(connection, 0);
            root.getBean(BusinessDataRepo.class).seedRoutes(connection);
            root.getBean(UserRepo.class).assignInitialScopes(
                connection,
                root.getBean(AuthInitializer.class).getData().path("accounts")
            );
            root.getBean(AuthInitializer.class).synchronize(root.getBean(EndpointAccess.class).all());
            return null;
        });
        assertEquals(hash, sql("SELECT md5(password_hash) FROM auth_users WHERE username='driver01'"));
        assertEquals("3", sql("SELECT failed_login_attempts FROM auth_users WHERE username='driver01'"));
        assertEquals("0", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='DRIVER'"));
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM auth_user_roles WHERE user_id=(SELECT id FROM auth_users WHERE username='driver01') AND scope_id>0"
            )
        );
        assertEquals("2", sql("SELECT count(*) FROM carrier_routes"));
    }

    @Test
    void emptyScopedAssignmentRetainsCasVersionAndHistoryFailureRollsBackTheMarker() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers ORDER BY id LIMIT 1"));
        long user = Long.parseLong(
            sql(
                "INSERT INTO auth_users(username,password_hash,display_name,must_change_password,email_verified_at) VALUES('scopecas','!DISABLED','CAS scope',false,CURRENT_TIMESTAMP) RETURNING id"
            )
        );
        sql("INSERT INTO auth_user_roles(user_id,role_code) VALUES(" + user + ",'CUSTOMER')");
        String path = "/api/v1/admin/access/scopes/" + scope + "/members/" + user;
        assertEquals(
            1,
            call(200, "PUT", path, "{\"roles\":[\"CUSTOMER\"],\"active\":true,\"version\":0}", admin)
                .path("version")
                .asLong()
        );
        sql(
            "CREATE FUNCTION refuse_scope_cas() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='SCOPE_MEMBERSHIP' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN NEW; END $$; CREATE TRIGGER refuse_scope_cas BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION refuse_scope_cas()"
        );
        String remove = "{\"roles\":[],\"active\":false,\"version\":1}";
        call(503, "PUT", path, remove, admin);
        assertEquals("1", sql("SELECT scope_membership_versions->>'" + scope + "' FROM auth_users WHERE id=" + user));
        assertEquals("1", sql("SELECT count(*) FROM auth_user_roles WHERE user_id=" + user + " AND scope_id=" + scope));
        sql("DROP TRIGGER refuse_scope_cas ON auth_access_changes; DROP FUNCTION refuse_scope_cas()");
        var empty = call(200, "PUT", path, remove, admin);
        assertEquals(2, empty.path("version").asLong());
        assertTrue(empty.path("roles").isEmpty());
        call(409, "PUT", path, "{\"roles\":[\"CUSTOMER\"],\"active\":true,\"version\":0}", admin);
        call(409, "PUT", path, "{\"roles\":[\"CUSTOMER\"],\"active\":true,\"version\":1}", admin);
        assertEquals(
            3,
            call(200, "PUT", path, "{\"roles\":[\"CUSTOMER\"],\"active\":true,\"version\":2}", admin)
                .path("version")
                .asLong()
        );
        long version = Long.parseLong(sql("SELECT version FROM auth_users WHERE id=" + user));
        call(
            200,
            "PUT",
            "/api/v1/admin/access/users/" + user + "/roles",
            "{\"roles\":[\"DRIVER\"],\"version\":" + version + "}",
            admin
        );
        var members = call(200, "GET", "/api/v1/admin/access/scopes/" + scope + "/members?userId=" + user, null, admin);
        assertEquals(4, members.path("content").get(0).path("version").asLong());
        assertTrue(members.path("content").get(0).path("roles").isEmpty());
    }

    @Test
    void compatibilityPermissionCreationStoresItsOperationAndRemainsAssignable() throws Exception {
        call(
            404,
            "POST",
            "/api/v1/admin/access/registry/permissions/EXTRA_DETAIL",
            "{\"nameVi\":\"Không còn registry permission\",\"nameEn\":\"Removed registry\",\"active\":true,\"version\":0}",
            admin
        );
        var value = call(
            201,
            "POST",
            "/api/v1/admin/access/functions",
            "{\"code\":\"EXTRA_DETAIL\",\"nameVi\":\"Chi tiết xe\",\"nameEn\":\"Vehicle detail\",\"active\":true,\"targets\":[\"GET /api/v1/admin/vehicles/{id}\"]}",
            admin
        );
        assertEquals("EXTRA_DETAIL", value.path("code").asText());
        assertEquals("1", sql("SELECT count(*) FROM auth_function_resources WHERE function_code='EXTRA_DETAIL'"));
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND column_name='operation_codes'"
            )
        );
    }

    /** Checksum sai dừng trước seed/DDL, không hợp thức hóa history hoặc thay hash hiện có. */
    @Test
    void checksumMismatchStopsBeforeChangingBusinessOrCredentials() throws Exception {
        String password = sql("SELECT password_hash FROM auth_users WHERE id=1");
        String carriers = sql("SELECT count(*) FROM carriers");
        sql("UPDATE vietroute_schema_history SET checksum=repeat('0',64) WHERE version='auth/001_schema.sql'");
        assertThrows(IllegalStateException.class, () -> root.getBean(DatabaseInitializer.class).afterPropertiesSet());
        assertEquals(password, sql("SELECT password_hash FROM auth_users WHERE id=1"));
        assertEquals(carriers, sql("SELECT count(*) FROM carriers"));
        assertEquals("10", sql("SELECT count(*) FROM vietroute_schema_history"));
    }

    /** Runtime không tự nhận baseline lạ; bảo trì history chỉ chạy bằng công cụ ngoài ứng dụng. */
    @Test
    void unexpectedHistoryRequiresExplicitOfflineRebaseline() throws Exception {
        String password = sql("SELECT password_hash FROM auth_users WHERE id=1");
        sql(
            "INSERT INTO vietroute_schema_history(version,checksum) VALUES('unsupported/001_schema.sql',repeat('0',64))"
        );
        assertThrows(IllegalStateException.class, () -> root.getBean(DatabaseInitializer.class).afterPropertiesSet());
        assertEquals(password, sql("SELECT password_hash FROM auth_users WHERE id=1"));
        assertEquals("11", sql("SELECT count(*) FROM vietroute_schema_history"));
        assertEquals("22", sql("SELECT count(*) FROM pg_tables WHERE schemaname=current_schema()"));
    }

    /** Repo thật phải commit để một instance khác đọc được; không thay bằng journal test double. */
    @Test
    void journalRepositoryCommitsAcrossReadersAndCleansExpiredRowsOnPostgresql() throws Exception {
        ApiJournalRepo repository = root.getBean(ApiJournalRepo.class);
        UUID id = UUID.randomUUID();
        ApiJournalEntry metadata = new ApiJournalEntry(
            id,
            Instant.now(),
            "postgresql-journal",
            "GET",
            "/api/v1/admin/carriers",
            "CarrierAPI#page",
            200,
            12,
            "OK000",
            "success.ok",
            "SUCCESS",
            "",
            1L,
            null
        );
        repository.append(
            new ApiJournalDetail(
                metadata,
                "test-node",
                "/api/v1/admin/carriers",
                Map.of("page", "0"),
                "{}",
                "{\"success\":true}",
                "JSON"
            )
        );
        ApiJournalRepo reader = new ApiJournalRepo(
            root.getBean("journalConnections", JdbcConnectionFactory.class),
            json
        );
        ApiJournalDetail stored = reader.find(id, 10080);
        assertEquals("testadmin", stored.getEntry().getUsername());
        assertEquals(1L, stored.getEntry().getUserId());
        assertEquals(Map.of("page", "0"), stored.getQuery());
        ApiJournalSearch search = new ApiJournalSearch();
        search.setTraceId("postgresql-journal");
        assertEquals(1, reader.page(search, 10080).getTotalElements());
        sql("UPDATE api_request_journal SET recorded_at=CURRENT_TIMESTAMP-INTERVAL '8 days' WHERE id='" + id + "'");
        assertTrue(reader.find(id, 10080) == null);
        repository.cleanup(10080, 10000);
        assertEquals("0", sql("SELECT count(*) FROM api_request_journal WHERE id='" + id + "'"));
    }

    /** Startup lặp lại baseline giữ mật khẩu, dữ liệu, timestamp history và không thêm bảng. */
    @Test
    void baselineStartupIsIdempotentAndPreservesCredentialsAndBusiness() throws Exception {
        String password = sql("SELECT password_hash FROM auth_users WHERE id=1");
        String carriers = sql("SELECT count(*) FROM carriers");
        String history = sql(
            "SELECT md5(string_agg(version||checksum||installed_at::text,',' ORDER BY version)) FROM vietroute_schema_history"
        );
        DatabaseInitializer initializer = root.getBean(DatabaseInitializer.class);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        assertEquals(password, sql("SELECT password_hash FROM auth_users WHERE id=1"));
        assertEquals(carriers, sql("SELECT count(*) FROM carriers"));
        assertEquals(
            history,
            sql(
                "SELECT md5(string_agg(version||checksum||installed_at::text,',' ORDER BY version)) FROM vietroute_schema_history"
            )
        );
        assertEquals("10", sql("SELECT count(*) FROM vietroute_schema_history"));
        assertEquals("22", sql("SELECT count(*) FROM pg_tables WHERE schemaname=current_schema()"));
        assertEquals(
            "8",
            sql("SELECT count(*) FROM pg_tables WHERE schemaname=current_schema() AND tablename LIKE 'auth_%'")
        );
    }

    /** Bộ startup thật tạo đủ dữ liệu, đúng scope/owner/assignment, giá/chỗ/tiền/history và login. */
    @Test
    void completeInitializationCreatesRunnableAccountsTripsAndBookingsWithCorrectScopes() throws Exception {
        initializeOperationalData();
        assertEquals("47", sql("SELECT count(*) FROM auth_users"));
        assertEquals("7", sql("SELECT count(*) FROM carriers"));
        assertEquals("25", sql("SELECT count(*) FROM carrier_vehicles"));
        assertEquals("25", sql("SELECT count(*) FROM carrier_drivers WHERE user_id IS NOT NULL"));
        assertEquals("34", sql("SELECT count(*) FROM category WHERE type='VALUE' AND ref_type='PROVINCE'"));
        assertEquals("3321", sql("SELECT count(*) FROM category WHERE type='VALUE' AND ref_type='WARD'"));
        assertEquals("14", sql("SELECT count(*) FROM category WHERE type='VALUE' AND ref_type='LOCATION'"));
        assertEquals("28", sql("SELECT count(*) FROM carrier_routes"));
        assertEquals("646", sql("SELECT count(*) FROM carrier_trips"));
        assertEquals("3866", sql("SELECT count(*) FROM bookings"));
        assertEquals("0", sql("SELECT count(*) FROM trip_events WHERE created_at>CURRENT_TIMESTAMP"));
        assertEquals("0", sql("SELECT count(*) FROM booking_events WHERE created_at>CURRENT_TIMESTAMP"));
        assertEquals(
            "0",
            sql("SELECT count(*) FROM bookings WHERE created_at>CURRENT_TIMESTAMP OR updated_at>CURRENT_TIMESTAMP")
        );
        assertEquals(
            "0",
            sql("SELECT count(*) FROM carrier_trips WHERE created_at>CURRENT_TIMESTAMP OR updated_at>CURRENT_TIMESTAMP")
        );
        assertEquals(
            "13",
            sql("SELECT count(*) FROM bookings WHERE source_code='WEB' AND status IN ('PENDING','CONFIRMED')")
        );
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM trip_segments s WHERE reserved_seats<0 OR reserved_seats>capacity OR reserved_seats<>(SELECT coalesce(sum(b.seats),0) FROM bookings b WHERE b.trip_id=s.trip_id AND b.pickup_order<=s.segment_order AND b.dropoff_order>s.segment_order AND b.status IN ('CONFIRMED','COMPLETED'))"
            )
        );
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM bookings WHERE total_price<>unit_price*seats OR refunded_amount>paid_amount OR paid_amount>total_price"
            )
        );
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM bookings b WHERE paid_amount<>(SELECT coalesce(sum(e.amount),0) FROM booking_events e WHERE e.booking_id=b.id AND e.event_type='PAYMENT') OR refunded_amount<>(SELECT coalesce(sum(e.amount),0) FROM booking_events e WHERE e.booking_id=b.id AND e.event_type='REFUND')"
            )
        );
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM carrier_trips a JOIN carrier_trips b ON a.id<b.id AND (a.driver_id=b.driver_id OR a.vehicle_id=b.vehicle_id) AND a.status IN ('OPEN','IN_PROGRESS') AND b.status IN ('OPEN','IN_PROGRESS') AND a.departure_at<b.end_at AND a.end_at>b.departure_at"
            )
        );
        long customerId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='custom01'"));
        long operatorId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='operator01'"));
        long driverId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='driver01'"));
        var customer = actor(customerId, "custom01", "CUSTOMER");
        var operator = actor(operatorId, "operator01", "OPERATOR");
        var driver = actor(driverId, "driver01", "DRIVER");
        // Tài liệu thuộc bộ bootstrap admin qua mapping DB, ba role nghiệp vụ không được xem trực tiếp.
        assertEquals(
            "2",
            sql(
                "SELECT count(*) FROM auth_function_resources WHERE function_code='ADMIN_CONSOLE' AND target IN ('GET /docs','GET /v3/api-docs')"
            )
        );
        for (Authentication ordinary : List.of(customer, operator, driver)) {
            call(403, "GET", "/docs", null, ordinary);
            call(403, "GET", "/v3/api-docs", null, ordinary);
        }
        call(200, "GET", "/docs", null, admin);
        assertEquals(
            200,
            mvc
                .perform(request(HttpMethod.GET, "/v3/api-docs").with(authentication(admin)))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        assertEquals(2, call(200, "GET", "/api/v1/my-bookings", null, customer).path("totalElements").asInt());
        assertEquals(71, call(200, "GET", "/api/v1/admin/trips", null, operator).path("totalElements").asInt());
        assertEquals(5, call(200, "GET", "/api/v1/driver/trips", null, driver).path("totalElements").asInt());
        long otherTrip = Long.parseLong(
            sql("SELECT min(t.id) FROM carrier_trips t JOIN carriers c ON c.id=t.carrier_id WHERE c.code='VRBACNAM'")
        );
        call(404, "GET", "/api/v1/admin/trips/" + otherTrip, null, operator);
        call(404, "GET", "/api/v1/driver/trips/" + otherTrip, null, driver);
        call(403, "GET", "/api/v1/admin/accounts", null, customer);
        long readyTrip = Long.parseLong(sql("SELECT id FROM carrier_trips WHERE code='VRINIT1R01D07'"));
        assertEquals(
            "IN_PROGRESS",
            call(
                200,
                "PUT",
                "/api/v1/driver/trips/" + readyTrip + "/progress",
                "{\"action\":\"START\",\"stopOrder\":1,\"version\":0,\"note\":\"Xác nhận khởi hành\"}",
                driver
            )
                .path("status")
                .asText()
        );
        var login = mvc
            .perform(
                request(HttpMethod.POST, "/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"username\":\"custom01\",\"password\":\"SeedTestOnly!2026Aa\"}")
            )
            .andReturn()
            .getResponse();
        assertEquals(200, login.getStatus());
        assertFalse(
            json.readTree(login.getContentAsString()).path("data").path("user").path("mustChangePassword").asBoolean()
        );
    }

    /** Restart không dịch ngày, tạo trùng, đổi hash hoặc phục hồi scope đã bị thu hồi. */
    @Test
    void completeInitializationIsIdempotentAndKeepsUserChangesAndRevokedScope() throws Exception {
        initializeOperationalData();
        String trips = sql(
            "SELECT md5(string_agg(id||code||departure_at::text||status,',' ORDER BY id)) FROM carrier_trips"
        );
        String bookings = sql(
            "SELECT md5(string_agg(id||request_key::text||paid_amount::text||status,',' ORDER BY id)) FROM bookings"
        );
        sql(
            "DELETE FROM auth_user_roles WHERE user_id=(SELECT id FROM auth_users WHERE username='operator01') AND scope_id>0"
        );
        sql("UPDATE carrier_trips SET operation_note='Ghi chú đã sửa',version=version+1 WHERE code='VRINIT1R01D07'");
        String changedHash = root.getBean(PasswordEncoder.class).encode("ChangedSeedTest!2026Aa");
        root.getBean(JdbcTransactions.class).execute(connection -> {
            try (
                var statement = connection.prepareStatement(
                    "UPDATE auth_users SET password_hash=? WHERE username='custom01'"
                )
            ) {
                statement.setString(1, changedHash);
                statement.executeUpdate();
            }
            return null;
        });
        initializeOperationalData();
        assertEquals(
            trips,
            sql("SELECT md5(string_agg(id||code||departure_at::text||status,',' ORDER BY id)) FROM carrier_trips")
        );
        assertEquals(
            bookings,
            sql(
                "SELECT md5(string_agg(id||request_key::text||paid_amount::text||status,',' ORDER BY id)) FROM bookings"
            )
        );
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM auth_user_roles WHERE user_id=(SELECT id FROM auth_users WHERE username='operator01') AND scope_id>0"
            )
        );
        assertEquals("Ghi chú đã sửa", sql("SELECT operation_note FROM carrier_trips WHERE code='VRINIT1R01D07'"));
        assertEquals(changedHash, sql("SELECT password_hash FROM auth_users WHERE username='custom01'"));
        assertEquals("47", sql("SELECT count(*) FROM auth_users"));
    }

    /** Lịch theo đồng hồ Việt Nam, đủ bốn lượt/ngày, đúng đầu quay lại và không chồng cùng xe/tài xế. */
    @Test
    void duyLongTimetableKeepsFourDailyLegsSixteenSeatsAndStableAnchor() throws Exception {
        initializeOperationalData();
        assertEquals("152", sql("SELECT count(*) FROM carrier_trips WHERE code LIKE 'DLG1%'"));
        assertEquals(
            "05:00-09:00|09:30-11:30|12:00-15:00|15:30-17:30",
            sql(
                """
                SELECT string_agg(start_time||'-'||end_time,'|' ORDER BY start_time)
                FROM (
                    SELECT DISTINCT to_char(departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh','HH24:MI') start_time,
                        to_char(end_at AT TIME ZONE 'Asia/Ho_Chi_Minh','HH24:MI') end_time
                    FROM carrier_trips WHERE code LIKE 'DLG1%'
                ) times
                """
            )
        );
        assertEquals(
            "0",
            sql(
                """
                SELECT count(*) FROM (
                    SELECT (departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                    FROM carrier_trips WHERE code LIKE 'DLG1%'
                    GROUP BY 1 HAVING count(*)<>4
                ) invalid_day
                """
            )
        );
        assertEquals(
            "0",
            sql(
                """
                SELECT count(*) FROM (
                    SELECT departure_at,lag(end_at) OVER (ORDER BY departure_at) previous_end
                    FROM carrier_trips WHERE code LIKE 'DLG1%'
                ) timetable_rows WHERE previous_end>departure_at
                """
            )
        );
        assertEquals(
            "0",
            sql(
                """
                SELECT count(*) FROM carrier_trips t JOIN carrier_drivers d ON d.id=t.driver_id
                JOIN carrier_vehicles v ON v.id=t.vehicle_id
                WHERE t.code LIKE 'DLG1%' AND (t.capacity<>16 OR v.capacity<>16
                    OR d.name<>'Phạm Thành Lương' OR t.driver_name<>'Phạm Thành Lương')
                """
            )
        );
        assertEquals(
            "22:00",
            sql(
                """
                SELECT min(to_char(departure_at AT TIME ZONE 'UTC','HH24:MI'))
                FROM carrier_trips WHERE code LIKE 'DLG1R25%'
                """
            )
        );
        String before = sql("SELECT md5(string_agg(code||departure_at::text,',' ORDER BY code)) FROM carrier_trips");
        root.getBean(JdbcTransactions.class).execute(connection -> {
            try (Statement query = connection.createStatement()) {
                query.execute("SET LOCAL TIME ZONE 'Pacific/Auckland'");
            }
            root.getBean(BusinessDataRepo.class).seedOperations(connection);
            return null;
        });
        assertEquals(
            before,
            sql("SELECT md5(string_agg(code||departure_at::text,',' ORDER BY code)) FROM carrier_trips")
        );
    }

    /** Account Duy Long dùng đúng scope/assignment; nhận PHONE, giữ chỗ và ghi thu trên giá snapshot. */
    @Test
    void duyLongAccountsCanSearchBookConfirmAndPayWithoutCrossCarrierAccess() throws Exception {
        initializeOperationalData();
        long carrier = Long.parseLong(sql("SELECT id FROM carriers WHERE code='DUYLONG'"));
        long driverId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='driverduylong'"));
        long operatorId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='operatorduylong'"));
        long customerId = Long.parseLong(sql("SELECT id FROM auth_users WHERE username='customduylong'"));
        Authentication driver = actor(driverId, "driverduylong", "DRIVER");
        Authentication operator = actor(operatorId, "operatorduylong", "OPERATOR");
        Authentication customer = actor(customerId, "customduylong", "CUSTOMER");
        JsonNode trips = call(200, "GET", "/api/v1/admin/trips?size=100", null, operator);
        assertEquals(152, trips.path("totalElements").asInt());
        for (JsonNode trip : trips.path("content")) assertEquals(carrier, trip.path("carrierId").asLong());
        long id = Long.parseLong(
            sql(
                """
                SELECT id FROM carrier_trips WHERE code LIKE 'DLG1R25%' AND status='OPEN'
                    AND departure_at>CURRENT_TIMESTAMP+INTERVAL '12 hours'
                ORDER BY departure_at LIMIT 1
                """
            )
        );
        String date = sql(
            "SELECT (departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM carrier_trips WHERE id=" + id
        );
        String from = sql("SELECT id FROM category WHERE ref_type='LOCATION' AND code='DLGHAIPHONG'");
        String to = sql("SELECT id FROM category WHERE ref_type='LOCATION' AND code='DLGHANOI'");
        JsonNode matches = call(
            200,
            "GET",
            "/api/v1/trips?fromLocationId=" +
                from +
                "&toLocationId=" +
                to +
                "&date=" +
                date +
                "&carrierId=" +
                carrier +
                "&seats=2",
            null,
            customer
        );
        assertEquals(2, matches.path("totalElements").asInt());
        String wardSearch =
            "/api/v1/trips?fromProvinceId=" +
            categoryId("PROVINCE", "HAIPHONG") +
            "&toProvinceId=" +
            categoryId("PROVINCE", "HANOI") +
            "&date=" +
            date +
            "&carrierId=" +
            carrier +
            "&fromWardId=" +
            categoryId("WARD", "11383") +
            "&toWardId=" +
            categoryId("WARD", "00070");
        assertEquals(2, call(200, "GET", wardSearch, null, customer).path("totalElements").asInt());
        assertEquals(
            0,
            call(
                200,
                "GET",
                wardSearch.replace(
                    "fromWardId=" + categoryId("WARD", "11383"),
                    "fromWardId=" + categoryId("WARD", "20200")
                ),
                null,
                customer
            )
                .path("totalElements")
                .asInt()
        );

        call(200, "GET", "/api/v1/driver/trips/" + id, null, driver);
        long foreign = Long.parseLong(sql("SELECT min(id) FROM carrier_trips WHERE carrier_id<>" + carrier));
        call(404, "GET", "/api/v1/admin/trips/" + foreign, null, operator);
        call(404, "GET", "/api/v1/driver/trips/" + foreign, null, driver);
        call(403, "GET", "/api/v1/admin/trips", null, customer);
        assertEquals(2, call(200, "GET", "/api/v1/my-bookings", null, customer).path("totalElements").asInt());
        long booking = call(
            201,
            "POST",
            "/api/v1/admin/bookings",
            bookingBody(id, 1, 2, 2, UUID.randomUUID()),
            operator
        )
            .path("id")
            .asLong();
        JsonNode confirmed = call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + booking + "/state",
            "{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\"Xác nhận Duy Long\"}",
            operator
        );
        assertEquals("360000.00", sql("SELECT total_price FROM bookings WHERE id=" + booking));
        call(
            200,
            "PUT",
            "/api/v1/admin/bookings/" + booking + "/payment",
            "{\"kind\":\"PAYMENT\",\"amount\":360000,\"note\":\"Đã nhận tiền\",\"version\":" +
                confirmed.path("version").asLong() +
                ",\"requestKey\":\"" +
                UUID.randomUUID() +
                "\"}",
            operator
        );
        assertEquals("360000.00", sql("SELECT paid_amount FROM bookings WHERE id=" + booking));
    }

    private void initializeOperationalData() {
        ConfigurationEnvironment original = root.getBean(ConfigurationEnvironment.class);
        Map<String, String> override = Map.ofEntries(
            Map.entry("VIETROUTE_DEFAULT_ACCOUNTS_ENABLED", "true"),
            Map.entry("VIETROUTE_BUSINESS_INIT_ENABLED", "true"),
            Map.entry("VIETROUTE_ACCESS_SCOPE_CAPACITY", "64"),
            Map.entry("VIETROUTE_DEFAULT_CUSTOMER_EMAIL", "seed-customer@example.invalid"),
            Map.entry("VIETROUTE_DEFAULT_DRIVER_EMAIL", "seed-driver@example.invalid"),
            Map.entry("VIETROUTE_DEFAULT_OPERATOR_EMAIL", "seed-operator@example.invalid"),
            Map.entry("VIETROUTE_DEFAULT_CUSTOMER_PASSWORD", "SeedTestOnly!2026Aa"),
            Map.entry("VIETROUTE_DEFAULT_DRIVER_PASSWORD", "SeedTestOnly!2026Aa"),
            Map.entry("VIETROUTE_DEFAULT_OPERATOR_PASSWORD", "SeedTestOnly!2026Aa")
        );
        ConfigurationEnvironment environment = new ConfigurationEnvironment(
            key -> override.getOrDefault(key, original.apply(key)),
            null
        );
        AppSettings settings = new AppSettings(environment);
        JdbcTransactions transactions = root.getBean(JdbcTransactions.class);
        AuthInitializer auth = new AuthInitializer(
            root.getBean(UserRepo.class),
            root.getBean(RoleRepo.class),
            settings,
            environment,
            root.getBean(PasswordEncoder.class),
            transactions,
            json
        );
        BusinessDataRepo repo = new BusinessDataRepo(json, settings);
        transactions.execute(connection -> {
            auth.initialize(connection);
            new BusinessDataInitializer(
                root.getBean(DatabaseInitializer.class),
                settings,
                environment,
                repo,
                root.getBean(UserRepo.class),
                auth,
                transactions
            ).afterPropertiesSet();
            return null;
        });
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
        root.getBean(JdbcTransactions.class).execute(connection -> {
            LegacyBusinessGrants.initialize(
                connection,
                root
                    .getBean(EndpointAccess.class)
                    .all()
                    .stream()
                    .map(FunctionDTO::getTarget)
                    .collect(Collectors.toSet())
            );
            return null;
        });
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
            new UserPrincipal(id, username, Set.of(role), 0),
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
        text = LegacyBusinessGrants.sql(text);
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

    private long categoryId(String refType, String code) throws Exception {
        return Long.parseLong(
            sql("SELECT id FROM category WHERE type='VALUE' AND ref_type='" + refType + "' AND code='" + code + "'")
        );
    }

    @Test
    void categoryUpgradeRetiresOnlyOldResourcesAndKeepsRevokedRolesInactive() throws Exception {
        String offlineSchema = "category_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String password = sql("SELECT password_hash FROM auth_users WHERE id=1");
        try (var connection = root.getBean(JdbcConnectionFactory.class).open()) {
            assertFalse(connection.getAutoCommit());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + offlineSchema);
                statement.execute("SET LOCAL search_path=" + offlineSchema + ",pg_temp");
                for (String version : MigrationCatalog.versions().subList(0, 8)) {
                    statement.execute(new String(MigrationCatalog.read(version), StandardCharsets.UTF_8));
                }
                try (
                    var insert = connection.prepareStatement(
                        "INSERT INTO auth_users(id,username,password_hash,display_name,token_version) VALUES(1,'legacymember',?,'Người kiểm thử',9)"
                    )
                ) {
                    insert.setString(1, password);
                    insert.executeUpdate();
                }
                statement.execute(
                    """
                    INSERT INTO auth_functions(code,name_vi,name_en,active)
                    VALUES('ADMIN_CONSOLE','Quản trị','Admin',false),('ONLY_OLD','Chỉ đọc loại xe','Old reader',false);
                    INSERT INTO auth_roles(code,name_vi,name_en,active) VALUES('LEGACY','Đã thu hồi','Revoked',false);
                    INSERT INTO auth_user_roles(user_id,role_code,active) VALUES(1,'LEGACY',false);
                    INSERT INTO auth_role_functions(role_code,function_code) VALUES('LEGACY','ONLY_OLD');
                    INSERT INTO auth_function_resources(function_code,kind,target,http_method,path_template,handler,description)
                    VALUES('ADMIN_CONSOLE','MENU','GET /catalogs/vehicle-types','GET','/catalogs/vehicle-types','Fixture','Old menu'),
                        ('ONLY_OLD','ENDPOINT','GET /api/v1/admin/vehicle-types','GET','/api/v1/admin/vehicle-types','Fixture','Old read');
                    """
                );
                for (String version : MigrationCatalog.versions().subList(8, 10)) {
                    statement.execute(new String(MigrationCatalog.read(version), StandardCharsets.UTF_8));
                }
                try (
                    var row = statement.executeQuery(
                        """
                        SELECT (SELECT count(*) FROM auth_function_resources WHERE function_code='ADMIN_CONSOLE'),
                            (SELECT count(*) FROM auth_function_resources WHERE function_code='ONLY_OLD'),
                            (SELECT active FROM auth_roles WHERE code='LEGACY'),
                            (SELECT active FROM auth_user_roles WHERE user_id=1),
                            (SELECT active FROM auth_functions WHERE code='ONLY_OLD'),
                            (SELECT active FROM auth_functions WHERE code='ADMIN_CONSOLE'),
                            (SELECT token_version FROM auth_users WHERE id=1),
                            (SELECT password_hash FROM auth_users WHERE id=1),
                            (SELECT count(*) FROM auth_access_changes WHERE event_type='CATEGORY_RESOURCES_MIGRATED'),
                            (SELECT version FROM auth_functions WHERE code='ONLY_OLD')
                        """
                    )
                ) {
                    assertTrue(row.next());
                    assertEquals(7, row.getInt(1));
                    assertEquals(0, row.getInt(2));
                    for (int index = 3; index <= 6; index++) assertFalse(row.getBoolean(index));
                    assertEquals(9, row.getLong(7));
                    assertEquals(password, row.getString(8));
                    assertEquals(2, row.getInt(9));
                    assertEquals(1, row.getLong(10));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void categoryTreeLoadsDirectChildrenAndDeniesRemovedEndpoints() throws Exception {
        long haiPhong = categoryId("PROVINCE", "HAIPHONG");
        long haNoi = categoryId("PROVINCE", "HANOI");
        assertEquals("34", sql("SELECT count(*) FROM category WHERE value_ref='PROVINCE'"));
        assertEquals("3321", sql("SELECT count(*) FROM category WHERE value_ref='WARD'"));
        assertEquals("31", sql("SELECT props->>'administrativeCode' FROM category WHERE id=" + haiPhong));
        assertEquals("01", sql("SELECT props->>'administrativeCode' FROM category WHERE id=" + haNoi));
        var empty = mvc.perform(get(Routes.CATEGORIES).param("refType", "WARD")).andReturn().getResponse();
        assertEquals(200, empty.getStatus());
        assertEquals(0, json.readTree(empty.getContentAsString()).at("/data/totalElements").asInt());
        var child = mvc
            .perform(
                get(Routes.CATEGORIES)
                    .param("refType", "WARD")
                    .param("parentId", Long.toString(haiPhong))
                    .param("size", "3")
            )
            .andReturn()
            .getResponse();
        assertEquals(200, child.getStatus());
        var page = json.readTree(child.getContentAsString()).path("data");
        assertEquals(3, page.path("content").size());
        assertTrue(page.path("hasNext").asBoolean());
        for (JsonNode value : page.path("content")) {
            assertEquals(haiPhong, value.path("parentId").asLong());
            assertEquals("WARD", value.path("refType").asText());
            assertTrue(value.path("code").asText().matches("[0-9]{5}"));
            assertFalse(value.has("createdBy"));
        }
        var roots = mvc
            .perform(get(Routes.ADMIN_CATEGORIES).param("refType", "PROVINCE").with(authentication(admin)))
            .andReturn()
            .getResponse();
        assertEquals(200, roots.getStatus());
        assertEquals(34, json.readTree(roots.getContentAsString()).at("/data/totalElements").asInt());
        var direct = mvc
            .perform(
                get(Routes.ADMIN_CATEGORIES).param("parentId", Long.toString(haiPhong)).with(authentication(admin))
            )
            .andReturn()
            .getResponse();
        assertEquals(200, direct.getStatus());
        for (JsonNode value : json.readTree(direct.getContentAsString()).at("/data/content")) {
            assertEquals(haiPhong, value.path("parentId").asLong());
        }
        call(400, "GET", Routes.ADMIN_CATEGORIES, null, admin);
        long group = Long.parseLong(sql("SELECT id FROM category WHERE group_code='VEHICLE_TYPE'"));
        call(404, "GET", "/api/v1/admin/catalogs" + "/" + group, null, admin);
        call(404, "DELETE", "/api/v1/admin/catalogs" + "/" + group, "{\"version\":0}", admin);
        call(400, "POST", Routes.ADMIN_CATEGORIES, "{\"code\":\"CATTEST\",\"name\":\"Thiếu nhóm\"}", admin);
        call(
            400,
            "POST",
            Routes.ADMIN_CATEGORIES,
            "{\"code\":\"CATTEST\",\"refType\":\"VEHICLE_TYPE\",\"name\":\"Xe thử\",\"props\":{\"seatCount\":0}}",
            admin
        );
        var seat = call(
            201,
            "POST",
            Routes.ADMIN_CATEGORIES,
            "{\"code\":\"CATTEST\",\"refType\":\"VEHICLE_TYPE\",\"name\":\"Xe thử\",\"props\":{\"seatCount\":16}}",
            admin
        );
        assertEquals(16, seat.at("/props/seatCount").asInt());
        assertEquals("VALUE", seat.path("type").asText());
        var updated = call(
            200,
            "PUT",
            Routes.ADMIN_CATEGORIES + "/" + seat.path("id").asLong(),
            "{\"name\":\"Xe thử\",\"description\":\"Xe phục vụ liên tỉnh\",\"version\":0,\"props\":{\"seatCount\":16}}",
            admin
        );
        assertEquals("Xe phục vụ liên tỉnh", updated.path("description").asText());
        seed();
        long ward = categoryId("WARD", "26824");
        String url = Routes.CATEGORIES + "?refType=LOCATION&parentId=" + ward;
        assertTrue(call(200, "GET", url, null, admin).path("totalElements").asInt() > 0);
        sql("UPDATE category SET active=false WHERE id=" + categoryId("PROVINCE", "HCM"));
        assertEquals(0, call(200, "GET", url, null, admin).path("totalElements").asInt());
    }

    private Authentication member(long scope, String role, String permissions, String username) throws Exception {
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES ('" +
                role +
                "','Kiểm thử','Test'); INSERT INTO auth_role_functions(role_code,function_code,operation_codes) SELECT '" +
                role +
                "',code,ARRAY[code] FROM auth_functions WHERE code IN (" +
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
                "'); INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES (" +
                scope +
                "," +
                user +
                ",'" +
                role +
                "')"
        );
        assignFixtureResources(role);
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
                ",'REPORTEMPTY'); INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES(" +
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
        sql(
            "INSERT INTO auth_role_functions(role_code,function_code,operation_codes) VALUES('REPORTER','REPORT_VIEW',ARRAY['REPORT_VIEW'])"
        );
        assignFixtureResources("REPORTER");
        call(200, "GET", "/operations/reports?lang=vi", null, reporter);
        call(200, "GET", "/operations/reports?lang=en", null, reporter);
        sql("UPDATE auth_user_roles SET active=false WHERE scope_id=" + own.scope() + " AND user_id=" + user);
        assertEquals(0, call(200, "GET", path, null, reporter).path("tripCount").asInt());
        call(404, "GET", path + "&carrierId=" + own.carrier(), null, reporter);
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'REPORT_READ') WHERE role_code='REPORTER'"
        );
        call(403, "GET", path, null, reporter);
        SecurityContextHolder.getContext().setAuthentication(reporter);
        assertThrows(BusinessException.class, () -> root.getBean(ReportService.class).summary(new ReportSearch()));
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
        long carrier = id("carriers", "VRMIENNAM"),
            driver = id("carrier_drivers", "DRVSOUTH02");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var staff = member(scope, "PROVISIONER", "'DRIVER_VIEW','DRIVER_READ','DRIVER_ASSIGN'", "provisioner");
        grantDriverDelegation("PROVISIONER", staff);
        String body =
            "{\"username\":\"NewDriver\",\"email\":\"NEWDRIVER@example.invalid\",\"password\":\"TestOnly!2026Aa\",\"version\":0}";
        var result = call(201, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        long user = result.path("id").asLong();
        assertEquals("newdriver", result.path("username").asText());
        assertTrue(result.path("mustChangePassword").asBoolean());
        assertFalse(result.path("emailVerificationRequired").asBoolean());
        assertFalse(result.path("driverActive").asBoolean());
        assertFalse(result.has("password"));
        assertTrue(result.path("email").isNull());
        assertTrue(
            root
                .getBean(PasswordEncoder.class)
                .matches("TestOnly!2026Aa", sql("SELECT password_hash FROM auth_users WHERE id=" + user))
        );
        assertEquals(
            "true:true:false:false",
            sql(
                "SELECT active||':'||must_change_password||':'||activation_pending||':'||(email_verified_at IS NULL) FROM auth_users WHERE id=" +
                    user
            )
        );
        assertEquals("newdriver@example.invalid", sql("SELECT email FROM auth_users WHERE id=" + user));
        assertEquals(
            "DRIVER",
            sql("SELECT string_agg(role_code,',') FROM auth_user_roles WHERE scope_id=0 AND user_id=" + user)
        );
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM auth_user_roles WHERE scope_id=" +
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
            new UserPrincipal(user, "newdriver", Set.of("DRIVER"), 1),
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
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE code='VRMIENNAM'"));
        long driver = id("carrier_drivers", "DRVSOUTH02"),
            foreign = id("carrier_drivers", "DRVNORTH01");
        var staff = member(scope, "SCOPEDPROVISION", "'DRIVER_READ','DRIVER_ASSIGN'", "scopedprovision");
        grantDriverDelegation("SCOPEDPROVISION", staff);
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
        sql(
            "INSERT INTO auth_role_functions(role_code,function_code,operation_codes) VALUES('DRIVER','ACCOUNT_READ',ARRAY['ACCOUNT_READ'])"
        );
        call(403, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        assertEquals("0", sql("SELECT count(*) FROM auth_users WHERE username='scopeaccount'"));
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'ACCOUNT_READ') WHERE role_code='DRIVER'; UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'DRIVER_ASSIGN') WHERE role_code='SCOPEDPROVISION'; DELETE FROM auth_function_resources WHERE function_code IN(SELECT function_code FROM auth_role_functions WHERE role_code='SCOPEDPROVISION') AND target='POST /api/v1/admin/drivers/{id}/account'"
        );
        assertFalse(
            call(200, "GET", "/api/v1/admin/drivers/" + driver, null, staff)
                .path("canAssign")
                .asBoolean()
        );
        call(403, "POST", "/api/v1/admin/drivers/" + driver + "/account", body, staff);
        SecurityContextHolder.getContext().setAuthentication(staff);
        assertThrows(BusinessException.class, () ->
            root.getBean(DriverService.class).provision(driver, new UserRequest(), (UserPrincipal) staff.getPrincipal())
        );
    }

    @Test
    void driverProvisioningHistoryFailureRollsBackCredentialMembershipAndProfile() throws Exception {
        seed();
        long driver = id("carrier_drivers", "DRVSOUTH02");
        String users = sql("SELECT count(*) FROM auth_users"),
            memberships = sql(
                "SELECT count(*) FROM (SELECT DISTINCT scope_id,user_id FROM auth_user_roles WHERE scope_id>0) memberships"
            );
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
        assertEquals(
            memberships,
            sql(
                "SELECT count(*) FROM (SELECT DISTINCT scope_id,user_id FROM auth_user_roles WHERE scope_id>0) memberships"
            )
        );
        assertEquals(
            "true:true:0",
            sql("SELECT (user_id IS NULL)||':'||active||':'||version FROM carrier_drivers WHERE id=" + driver)
        );
    }

    @Test
    void concurrentDriverProvisioningHasOneAccountAndAssignedTripBlocksIdentityReplacement() throws Exception {
        seed();
        long driver = id("carrier_drivers", "DRVSOUTH02");
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
        // Ba tỉnh nền đã được nhập cùng dữ liệu hành chính khi DB_INIT chạy.
        assertEquals(13, seed().path("created").asInt());
        var repeat = seed();
        assertEquals(0, repeat.path("created").asInt());
        assertEquals(21, repeat.path("reused").asInt());
        assertEquals("2", sql("SELECT count(*) FROM carriers"));
        assertEquals("2", sql("SELECT count(*) FROM auth_users"));
        // API cũ không còn được đăng ký; FK có kiểu được kiểm trong ca Category tree.
        call(404, "GET", "/api/v1/admin/vehicle-types/1", null, admin);
        assertEquals(
            3,
            call(200, "GET", "/api/v1/categories?refType=VEHICLE_TYPE&size=2", null, admin)
                .path("totalElements")
                .asInt()
        );
        assertEquals(
            0,
            call(200, "GET", "/api/v1/categories?refType=VEHICLE_TYPE&query=%25_%27", null, admin)
                .path("totalElements")
                .asInt()
        );
    }

    @Test
    void publicCarrierLookupHasIndependentProjectionPagingAndPublicationState() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM");
        long other = id("carriers", "VRBACNAM");
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
        sql("UPDATE carriers SET scope_active=false WHERE scope_id=" + scope);
        call(404, "GET", "/api/v1/carriers/" + carrier, null, admin);
        assertEquals(0, call(200, "GET", "/api/v1/carriers", null, admin).path("totalElements").asInt());
    }

    @Test
    void membershipFiltersCountRowsIdsAndPickerWithoutLeakingOtherCarrier() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM");
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
            call(200, "GET", "/api/v1/admin/vehicles?carrierId=" + id("carriers", "VRBACNAM"), null, operator)
                .path("totalElements")
                .asInt()
        );
        var picker = call(200, "GET", "/api/v1/admin/vehicles/carriers", null, operator);
        assertEquals(1, picker.path("totalElements").asInt());
        assertFalse(picker.toString().contains("phone"));
        long other = id("carrier_vehicles", "VRNORTH01");
        call(404, "GET", "/api/v1/admin/vehicles/" + other, null, operator);
        long type = categoryId("VEHICLE_TYPE", "SEAT");
        String body =
            "{\"plateNumber\":\"DEMO-999\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":20,\"active\":true,\"version\":0}";
        call(404, "PUT", "/api/v1/admin/vehicles/" + other, body, operator);
        call(403, "GET", "/api/v1/admin/carriers", null, operator);
        call(403, "GET", "/operations/categories", null, operator);
        call(403, "POST", "/api/v1/admin/demo", "{}", operator);
        // UPDATE toàn cục từ role khác không được union sang role scoped chỉ còn READ.
        long operatorId = ((UserPrincipal) operator.getPrincipal()).id();
        sql(
            "INSERT INTO auth_roles(code,name_vi,name_en) VALUES ('GLOBALWRITE','Test','Test'); INSERT INTO auth_role_functions(role_code,function_code,operation_codes) VALUES ('GLOBALWRITE','VEHICLE_UPDATE',ARRAY['VEHICLE_UPDATE']); INSERT INTO auth_user_roles(user_id,role_code) VALUES (" +
                operatorId +
                ",'GLOBALWRITE'); UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'VEHICLE_UPDATE') WHERE role_code='TESTOP'"
        );
        var scopedReadOnly = call(200, "GET", "/api/v1/admin/vehicles", null, operator);
        assertFalse(scopedReadOnly.path("content").get(0).path("canUpdate").asBoolean());
        call(404, "PUT", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "VRSOUTH01"), body, operator);
        // Thu hồi membership trong DB có hiệu lực ở lần kiểm tiếp theo dù principal vẫn như cũ.
        sql("UPDATE auth_user_roles SET active=false WHERE scope_id=" + scope);
        assertEquals(0, call(200, "GET", "/api/v1/admin/vehicles", null, operator).path("totalElements").asInt());
    }

    @Test
    void viewReadAndTypePermissionsStayIndependentInBothContexts() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var view = member(scope, "VIEWONLY", "'VEHICLE_VIEW','CATEGORY_VIEW'", "testviewer");
        call(200, "GET", "/operations/vehicles", null, view);
        call(200, "GET", "/operations/categories", null, view);
        call(403, "GET", "/api/v1/admin/vehicles", null, view);
        call(403, "GET", "/api/v1/admin/categories?refType=PROVINCE", null, view);
        SecurityContextHolder.getContext().setAuthentication(view);
        assertThrows(BusinessException.class, () -> root.getBean(VehicleService.class).page(new CarrierSearch()));
        var read = member(scope, "TYPEONLY", "'CATEGORY_READ'", "testtypereader");
        call(200, "GET", "/api/v1/admin/categories?refType=VEHICLE_TYPE", null, read);
        call(403, "GET", "/operations/categories", null, read);
        call(403, "POST", "/api/v1/admin/categories", "{}", read);
    }

    @Test
    void plateUniquenessParentStatusVersionsAndDriverAccountAreEnforced() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM");
        long vehicle = id("carrier_vehicles", "VRSOUTH01");
        long type = categoryId("VEHICLE_TYPE", "SEAT");
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
            "{\"code\":\"VRBACNAM\",\"name\":\"Nhà xe đang dùng\",\"phone\":\"0000000000\",\"description\":\"Giữ nguyên\"}",
            admin
        )
            .path("id")
            .asLong();
        call(409, "POST", "/api/v1/admin/demo", "{}", admin);
        assertEquals("0", sql("SELECT count(*) FROM category WHERE code LIKE 'DEMO%'"));
        assertEquals("0", sql("SELECT count(*) FROM category WHERE type='VALUE' AND ref_type='LOCATION'"));
        assertEquals("1", sql("SELECT count(*) FROM carriers"));
        assertEquals("Giữ nguyên", sql("SELECT description FROM carriers WHERE id=" + carrier));
    }

    @Test
    void concurrentUpdatesHaveOneWinner() throws Exception {
        seed();
        long vehicle = id("carrier_vehicles", "VRSOUTH01");
        long type = categoryId("VEHICLE_TYPE", "SEAT");
        String body =
            "{\"plateNumber\":\"DEMO-001\",\"vehicleTypeId\":" +
            type +
            ",\"capacity\":28,\"active\":true,\"version\":0}";
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
    }

    @Test
    void scopeHistoryFailureAndCapacityCannotLeaveOrphanCarrierOrScope() throws Exception {
        sql(
            "CREATE FUNCTION fail_business_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''Business history test failure''; END'; CREATE TRIGGER fail_business_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_business_history()"
        );
        String body =
            "{\"code\":\"TESTCAPACITY\",\"name\":\"Test capacity\",\"phone\":\"0000000000\",\"description\":\"Test\"}";
        call(503, "POST", "/api/v1/admin/carriers", body, admin);
        assertEquals("0", sql("SELECT count(*) FROM carriers"));
        assertEquals("0", sql("SELECT count(*) FROM carriers"));
        sql("DROP TRIGGER fail_business_history ON auth_access_changes; DROP FUNCTION fail_business_history()");
        seed();
        assertEquals("2", sql("SELECT count(*) FROM auth_access_changes WHERE event_type='SCOPE_CREATED'"));
        call(409, "POST", "/api/v1/admin/carriers", body, admin);
        assertEquals("2", sql("SELECT count(*) FROM carriers"));
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

    @Test
    void inactiveAllScopesRoleStillProtectsFunctionFromScopedEditor() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var editor = member(scope, "SCOPED_EDITOR", "'VEHICLE_READ'", "scopededitor");
        grantResources(
            "SCOPED_EDITOR",
            0,
            Set.of("PUT /api/v1/admin/access/functions/{code}", "GET /api/v1/admin/vehicles"),
            admin,
            200
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/functions",
            "{\"code\":\"WIDE_FUNCTION\",\"nameVi\":\"Global\",\"nameEn\":\"Global\",\"description\":\"Giữ mô tả\",\"icon\":\"menu\",\"sortOrder\":5,\"active\":false,\"targets\":[\"GET /api/v1/admin/vehicles\"]}",
            admin
        );
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/WIDE_ROLE",
            "{\"nameVi\":\"Global\",\"nameEn\":\"Global\",\"allScopes\":true,\"active\":false}",
            admin
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/access/registry/roles/WIDE_ROLE",
            "{\"nameVi\":\"Đổi tên\",\"nameEn\":\"Renamed\",\"version\":0}",
            admin
        );
        assertEquals("true:false", sql("SELECT all_scopes||':'||active FROM auth_roles WHERE code='WIDE_ROLE'"));
        call(
            200,
            "PUT",
            "/api/v1/admin/access/functions/WIDE_FUNCTION",
            "{\"code\":\"WIDE_FUNCTION\",\"nameVi\":\"Đổi tên\",\"nameEn\":\"Renamed\",\"targets\":[\"GET /api/v1/admin/vehicles\"],\"version\":0}",
            admin
        );
        assertEquals(
            "Giữ mô tả:menu:5:false",
            sql(
                "SELECT description||':'||icon||':'||sort_order||':'||active FROM auth_functions WHERE code='WIDE_FUNCTION'"
            )
        );
        call(
            200,
            "PUT",
            "/api/v1/admin/access/roles/WIDE_ROLE/functions",
            "{\"functionCodes\":[\"WIDE_FUNCTION\"],\"version\":1}",
            admin
        );
        call(
            403,
            "PUT",
            "/api/v1/admin/access/functions/WIDE_FUNCTION",
            "{\"code\":\"WIDE_FUNCTION\",\"nameVi\":\"Changed\",\"nameEn\":\"Changed\",\"targets\":[\"GET /api/v1/admin/vehicles\"],\"version\":1}",
            editor
        );
        assertEquals("1", sql("SELECT version FROM auth_functions WHERE code='WIDE_FUNCTION'"));
    }

    @Test
    void sharedRequestsKeepCreateUpdateValidationAndImmutableParentIdentity() throws Exception {
        // Fixture giới hạn hai scope; tạo đúng hai cha, không seed thêm scope không dùng.
        String carrierBody =
            "{\"code\":\"SHAREDCARRIER\",\"name\":\"Nhà xe request chung\",\"phone\":\"0901234567\",\"description\":\"\"}";
        long carrier = call(201, "POST", "/api/v1/admin/carriers", carrierBody, admin).path("id").asLong();
        String carrierUpdate = "{\"name\":\"Tên mới\",\"phone\":\"0901234567\",\"description\":\"\"}";
        call(400, "PUT", "/api/v1/admin/carriers/" + carrier, carrierUpdate, admin);
        call(400, "POST", "/api/v1/admin/carriers", carrierUpdate, admin);
        call(200, "PUT", "/api/v1/admin/carriers/" + carrier, carrierBody.replace("}", ",\"version\":0}"), admin);
        call(
            400,
            "PUT",
            "/api/v1/admin/carriers/" + carrier,
            carrierBody.replace("SHAREDCARRIER", "OTHERIDENTITY").replace("}", ",\"version\":1}"),
            admin
        );
        assertEquals("SHAREDCARRIER", sql("SELECT code FROM carriers WHERE id=" + carrier));
        assertEquals("1", sql("SELECT version FROM carriers WHERE id=" + carrier));

        long otherCarrier = call(
            201,
            "POST",
            "/api/v1/admin/carriers",
            carrierBody.replace("SHAREDCARRIER", "OTHERCARRIER"),
            admin
        )
            .path("id")
            .asLong();
        long vehicleType = Long.parseLong(
            sql("SELECT min(id) FROM category WHERE type='VALUE' AND ref_type='VEHICLE_TYPE' AND active")
        );
        String vehicleBody =
            "{\"carrierId\":" +
            carrier +
            ",\"code\":\"SHAREDVEHICLE\",\"plateNumber\":\"97A-123.45\",\"vehicleTypeId\":" +
            vehicleType +
            ",\"capacity\":20}";
        long vehicle = call(201, "POST", "/api/v1/admin/vehicles", vehicleBody, admin).path("id").asLong();
        call(
            400,
            "PUT",
            "/api/v1/admin/vehicles/" + vehicle,
            vehicleBody
                .replace("\"carrierId\":" + carrier, "\"carrierId\":" + otherCarrier)
                .replace("}", ",\"version\":0}"),
            admin
        );
        assertEquals(Long.toString(carrier), sql("SELECT carrier_id FROM carrier_vehicles WHERE id=" + vehicle));

        long province = categoryId("WARD", "26824");
        long otherProvince = categoryId("WARD", "20200");
        String locationBody =
            "{\"code\":\"SHAREDLOCATION\",\"refType\":\"LOCATION\",\"parentId\":" +
            province +
            ",\"name\":\"Điểm kiểm thử\",\"props\":{\"nameEn\":\"Test place\",\"address\":\"Địa chỉ kiểm thử\",\"latitude\":10.1,\"longitude\":106.2}}";
        var createdLocation = call(201, "POST", "/api/v1/admin/categories", locationBody, admin);
        assertEquals(0, createdLocation.path("version").asLong());
        long location = createdLocation.path("id").asLong();
        call(
            400,
            "PUT",
            "/api/v1/admin/categories/" + location,
            locationBody
                .replace("\"parentId\":" + province, "\"parentId\":" + otherProvince)
                .replaceFirst("}$", ",\"version\":0}"),
            admin
        );
        assertEquals(
            Long.toString(province),
            sql("SELECT parent_id FROM category WHERE ref_type='LOCATION' AND id=" + location)
        );
    }

    @Test
    void allEightJpaModelsMapEveryRealColumnIncludingGeneratedScopeKeys() throws Exception {
        var models = List.of(
            User.class,
            Role.class,
            UserRole.class,
            Function.class,
            RoleFunction.class,
            FunctionResource.class,
            AccountChallenge.class,
            AccessChange.class
        );
        try (var connection = root.getBean(JdbcConnectionFactory.class).open()) {
            for (var model : models) {
                String table = model.getAnnotation(Table.class).name();
                var mapped = new LinkedHashSet<String>();
                for (Class<?> type = model; type != Object.class; type = type.getSuperclass()) {
                    for (var field : type.getDeclaredFields()) {
                        var column = field.getAnnotation(Column.class);
                        if (column != null) mapped.add(column.name());
                    }
                }
                try (
                    var query = connection.prepareStatement(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?"
                    )
                ) {
                    query.setString(1, table);
                    var actual = new LinkedHashSet<String>();
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) actual.add(rows.getString(1));
                    }
                    assertEquals(actual, mapped, table);
                }
            }
        }
    }

    @Test
    void businessJpaModelsCoverRealColumnsGeneratedValuesAndCompositeKeys() throws Exception {
        List<Class<?>> models = root
            .getBean(EntityManagerFactory.class)
            .getMetamodel()
            .getEntities()
            .stream()
            .filter(model -> model.getJavaType().getName().startsWith("vn.vietroute.business."))
            .<Class<?>>map(EntityType::getJavaType)
            .toList();
        assertEquals(12, models.size());
        try (Connection connection = root.getBean(JdbcConnectionFactory.class).open()) {
            for (Class<?> model : models) {
                String table = model.getAnnotation(Table.class).name();
                Map<String, Column> mapped = new LinkedHashMap<>();
                Set<String> ids = new LinkedHashSet<>();
                for (Class<?> type = model; type != Object.class; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        Column column = field.getAnnotation(Column.class);
                        if (column == null) continue;
                        mapped.put(column.name(), column);
                        if (field.isAnnotationPresent(Id.class)) ids.add(column.name());
                    }
                }
                Set<String> columns = new LinkedHashSet<>();
                try (
                    PreparedStatement query = connection.prepareStatement(
                        "SELECT column_name,is_generated FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?"
                    )
                ) {
                    query.setString(1, table);
                    try (ResultSet rows = query.executeQuery()) {
                        while (rows.next()) {
                            String name = rows.getString(1);
                            columns.add(name);
                            if ("ALWAYS".equals(rows.getString(2))) {
                                assertTrue(mapped.containsKey(name), table + "." + name);
                                assertFalse(mapped.get(name).insertable(), table + "." + name);
                                assertFalse(mapped.get(name).updatable(), table + "." + name);
                            }
                        }
                    }
                }
                assertEquals(columns, mapped.keySet(), table);
                Set<String> keys = new LinkedHashSet<>();
                try (ResultSet rows = connection.getMetaData().getPrimaryKeys(null, connection.getSchema(), table)) {
                    while (rows.next()) keys.add(rows.getString("COLUMN_NAME"));
                }
                assertEquals(keys, ids, table + " primary key");
            }
        }
    }

    /** Chuyển kiểm PK/index ra regression; metadata index chỉ khai báo bằng annotation JPA chuẩn. */
    @Test
    void migrationsCreateExpectedPrimaryKeysAndJpaIndexes() throws Exception {
        try (Connection connection = root.getBean(JdbcConnectionFactory.class).open()) {
            assertMigrationMetadata(connection);
        }
    }

    /** Kiểm âm tính trên transaction test: thiếu/sai index hoặc PK phải được regression phát hiện. */
    @Test
    void schemaRegressionDetectsMissingIndexesWrongColumnsAndPrimaryKeys() throws Exception {
        try (Connection connection = root.getBean(JdbcConnectionFactory.class).open()) {
            assertFalse(connection.getAutoCommit());
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX idx_bookings_trip_status");
                assertThrows(AssertionError.class, () -> assertMigrationMetadata(connection));
                statement.execute("CREATE INDEX idx_bookings_trip_status ON bookings(trip_id,id)");
                assertThrows(AssertionError.class, () -> assertMigrationMetadata(connection));
                statement.execute("DROP INDEX idx_bookings_trip_status");
                statement.execute("CREATE INDEX idx_bookings_trip_status ON bookings(trip_id,status,id)");
                assertMigrationMetadata(connection);
                statement.execute("ALTER TABLE auth_account_challenges DROP CONSTRAINT auth_account_challenges_pkey");
                assertThrows(AssertionError.class, () -> assertMigrationMetadata(connection));
            } finally {
                connection.rollback();
            }
            root.getBean(JdbcSchema.class).select(connection);
            assertMigrationMetadata(connection);
        }
    }

    private void assertMigrationMetadata(Connection connection) throws Exception {
        List<Class<?>> models = root
            .getBean(EntityManagerFactory.class)
            .getMetamodel()
            .getEntities()
            .stream()
            .<Class<?>>map(EntityType::getJavaType)
            .toList();
        assertEquals(21, models.size());
        // Bốn bảng dùng ID kỹ thuật UNIQUE cho JPA nhưng PK nghiệp vụ trong migration vẫn là khóa ghép.
        Map<String, List<String>> businessKeys = Map.of(
            "auth_account_challenges",
            List.of("user_id", "purpose"),
            "auth_function_resources",
            List.of("function_code", "kind", "target"),
            "auth_role_functions",
            List.of("role_code", "function_code"),
            "auth_user_roles",
            List.of("user_id", "role_code", "scope_id")
        );
        DatabaseMetaData metadata = connection.getMetaData();
        String databaseSchema = connection.getSchema();
        assertEquals(schema, databaseSchema);
        for (Class<?> model : models) {
            Table table = model.getAnnotation(Table.class);
            List<String> mappedIds = new ArrayList<>();
            for (Class<?> type = model; type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) mappedIds.add(field.getAnnotation(Column.class).name());
                }
            }
            Map<Short, String> actualKeys = new TreeMap<>();
            try (ResultSet rows = metadata.getPrimaryKeys(null, databaseSchema, table.name())) {
                while (rows.next()) actualKeys.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME"));
            }
            assertEquals(
                businessKeys.getOrDefault(table.name(), mappedIds),
                List.copyOf(actualKeys.values()),
                table.name()
            );
            Map<String, Map<Short, String>> actualIndexes = new LinkedHashMap<>();
            Map<String, Boolean> unique = new LinkedHashMap<>();
            try (ResultSet rows = metadata.getIndexInfo(null, databaseSchema, table.name(), false, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (name == null || column == null) continue;
                    actualIndexes
                        .computeIfAbsent(name, ignored -> new TreeMap<>())
                        .put(rows.getShort("ORDINAL_POSITION"), column);
                    unique.put(name, !rows.getBoolean("NON_UNIQUE"));
                }
            }
            for (Index index : table.indexes()) {
                String label = table.name() + "." + index.name();
                assertTrue(actualIndexes.containsKey(index.name()), label);
                assertEquals(
                    Arrays.stream(index.columnList().split(",")).map(String::trim).toList(),
                    List.copyOf(actualIndexes.get(index.name()).values()),
                    label
                );
                assertEquals(index.unique(), unique.get(index.name()), label);
            }
        }
    }

    @Test
    void jpaUserCrudAndJdbcHistoryShareRollbackAndOptimisticVersion() throws Exception {
        String body =
            "{\"username\":\"jpatxuser\",\"displayName\":\"Người dùng JPA\",\"password\":\"TestOnly!2026Aa\",\"roles\":[\"CUSTOMER\"]}";
        sql(
            "CREATE FUNCTION fail_jpa_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test history failure''; END'; CREATE TRIGGER fail_jpa_history BEFORE INSERT ON auth_access_changes FOR EACH ROW EXECUTE FUNCTION fail_jpa_history()"
        );
        call(503, "POST", "/api/v1/admin/accounts", body, admin);
        assertEquals("0", sql("SELECT count(*) FROM auth_users WHERE username='jpatxuser'"));
        sql("DROP TRIGGER fail_jpa_history ON auth_access_changes; DROP FUNCTION fail_jpa_history()");
        var created = call(201, "POST", "/api/v1/admin/accounts", body, admin);
        long id = created.path("id").asLong();
        long version = created.path("version").asLong();
        assertFalse(created.has("passwordHash"));
        assertFalse(created.has("password"));
        var update = "{\"displayName\":\"Tên mới\",\"version\":" + version + "}";
        assertEquals(
            version + 1,
            call(200, "PUT", "/api/v1/admin/accounts/" + id, update, admin)
                .path("version")
                .asLong()
        );
        call(409, "PUT", "/api/v1/admin/accounts/" + id, update, admin);
        assertEquals("Tên mới", sql("SELECT display_name FROM auth_users WHERE id=" + id));
    }

    private void approve(Authentication who, String target) {
        SecurityContextHolder.getContext().setAuthentication(who);
        SecurityContextHolder.getContext().setAuthentication(
            root.getBean(EndpointAccess.class).authorize(who, target, false)
        );
    }

    private void grantDriverDelegation(String role, Authentication staff) throws Exception {
        var targets = root
            .getBean(JdbcTransactions.class)
            .execute(connection -> root.getBean(RoleFunctionRepo.class).targets("DRIVER"));
        var combined = new LinkedHashSet<>(targets);
        combined.addAll(LegacyBusinessGrants.targets(Set.of("DRIVER_READ", "DRIVER_ASSIGN", "DRIVER_VIEW")));
        grantResources(role, 0, combined, admin, 200);
    }

    private int fixtureBinding;

    private JsonNode grantResources(String role, long version, Set<String> targets, Authentication who, int status)
        throws Exception {
        if (status == 400) return call(
            400,
            "POST",
            "/api/v1/admin/access/functions",
            json.writeValueAsString(
                Map.of("code", "INVALID_BINDING", "nameVi", "Test", "nameEn", "Test", "targets", targets)
            ),
            admin
        );
        String function = "FIXTURE_BINDING_" + ++fixtureBinding;
        root.getBean(JdbcTransactions.class).execute(connection -> {
            try (
                var query = connection.prepareStatement(
                    "INSERT INTO auth_functions(code,name_vi,name_en,resources_initialized) VALUES(?,'Fixture','Fixture',true)"
                )
            ) {
                query.setString(1, function);
                query.executeUpdate();
            }
            var catalog = root.getBean(EndpointAccess.class);
            var endpoints = targets.stream().map(catalog::find).toList();
            if (endpoints.stream().noneMatch(Objects::isNull)) root.getBean(FunctionResourceRepo.class).replace(
                connection,
                function,
                endpoints,
                0
            );
            return null;
        });
        return call(
            status,
            "PUT",
            "/api/v1/admin/access/roles/" + role + "/functions",
            json.writeValueAsString(Map.of("functionCodes", Set.of(function), "version", version)),
            who
        );
    }

    @Test
    void restartPreservesMappingsPasswordAndNeverEnrollsNewSourceEndpoints() throws Exception {
        String hash = sql("SELECT password_hash FROM auth_users WHERE id=1");
        sql("DELETE FROM auth_role_functions WHERE role_code='OPERATOR'");
        root.getBean(AuthInitializer.class).synchronize(root.getBean(EndpointAccess.class).all());
        root.getBean(AuthInitializer.class).synchronize(root.getBean(EndpointAccess.class).all());
        assertEquals(hash, sql("SELECT password_hash FROM auth_users WHERE id=1"));
        assertEquals("0", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='OPERATOR'"));
        assertEquals("t", sql("SELECT functions_initialized FROM auth_roles WHERE code='OPERATOR'"));
    }

    @Test
    void nativeResourcesSeparateListDetailMenuAndRevocationAcrossWarContexts() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        var user = member(scope, "NATIVEREADER", "'VEHICLE_READ','VEHICLE_VIEW'", "nativereader");
        String page = "GET /operations/vehicles",
            list = "GET /api/v1/admin/vehicles",
            detail = list + "/{id}";
        grantResources("NATIVEREADER", 0, Set.of(page, list), admin, 200);
        assertEquals(
            "0",
            sql(
                "SELECT (jsonb_array_length(after_state->'functions')-1)::text FROM auth_access_changes WHERE event_type='ROLE_FUNCTIONS' AND target_key='NATIVEREADER' ORDER BY id DESC LIMIT 1"
            )
        );
        assertEquals(2, call(200, "GET", "/api/v1/admin/vehicles", null, user).path("totalElements").asInt());
        call(200, "GET", "/operations/vehicles", null, user);
        long vehicle = id("carrier_vehicles", "VRSOUTH01");
        call(403, "GET", "/api/v1/admin/vehicles/" + vehicle, null, user);
        call(403, "GET", "/api/v1/admin/vehicles/carriers", null, user);
        call(403, "GET", "/operations/routes", null, user);
        grantResources("NATIVEREADER", 0, Set.of(detail), admin, 409);
        grantResources("NATIVEREADER", 1, Set.of("GET /api/v1/carriers"), admin, 400);
        grantResources("NATIVEREADER", 1, Set.of("GET /api/v1/auth/me"), admin, 400);
        grantResources("NATIVEREADER", 1, Set.of(page, detail), admin, 200);
        call(403, "GET", "/api/v1/admin/vehicles", null, user);
        call(200, "GET", "/api/v1/admin/vehicles/" + vehicle, null, user);
        assertEquals("1", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='NATIVEREADER'"));
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
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM (SELECT DISTINCT rf.role_code,fr.kind,fr.target FROM auth_role_functions rf JOIN auth_function_resources fr ON fr.function_code=rf.function_code) WHERE role_code='NATIVEREADER'"
            )
        );
    }

    @Test
    void nativeScopeCannotBorrowDetailEndpointFromRoleOutsideThatScope() throws Exception {
        seed();
        long first = id("carriers", "VRMIENNAM"),
            second = id("carriers", "VRBACNAM");
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
                "INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES(" +
                scopeA +
                "," +
                userId +
                ",'SCOPEDDETAIL'); " +
                "INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES(" +
                scopeB +
                "," +
                userId +
                ",'SCOPEDLIST')"
        );
        assertEquals(4, call(200, "GET", "/api/v1/admin/vehicles", null, user).path("totalElements").asInt());
        call(200, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "VRSOUTH01"), null, user);
        call(404, "GET", "/api/v1/admin/vehicles/" + id("carrier_vehicles", "VRNORTH01"), null, user);
    }

    @Test
    void compatibilityAssignmentsAndRoleDeletionRetainExactResourceHistory() throws Exception {
        call(
            201,
            "POST",
            "/api/v1/admin/access/registry/roles/REMOVABLE",
            "{\"nameVi\":\"Vai trò thử\",\"nameEn\":\"Test role\",\"active\":true,\"version\":0}",
            admin
        );
        grantResources("REMOVABLE", 0, Set.of("GET /api/v1/admin/vehicles"), admin, 200);
        call(200, "DELETE", "/api/v1/admin/access/registry/roles/REMOVABLE?version=1", null, admin);
        assertEquals("t", sql("SELECT deleted FROM auth_roles WHERE code='REMOVABLE'"));
        assertEquals("0", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='REMOVABLE'"));
        assertEquals(
            "1",
            sql("SELECT count(*) FROM auth_access_changes WHERE target_key='REMOVABLE' AND event_type='ROLE_DELETED'")
        );
    }

    @Test
    void nativeDelegationAndHistoryFailureCannotEscalateOrPartiallyCommit() throws Exception {
        seed();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        var delegate = member(scope, "NATIVEDELEGATE", "'ACCESS_ASSIGN','VEHICLE_READ'", "nativedelegate");
        grantResources(
            "NATIVEDELEGATE",
            0,
            Set.of("PUT /api/v1/admin/access/roles/{role}/functions", "GET /api/v1/admin/vehicles"),
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
        assertEquals(
            "1",
            sql(
                "SELECT count(*) FROM (SELECT DISTINCT rf.role_code,fr.kind,fr.target FROM auth_role_functions rf JOIN auth_function_resources fr ON fr.function_code=rf.function_code) WHERE role_code='NATIVETARGET'"
            )
        );
        assertEquals("1", sql("SELECT count(*) FROM auth_role_functions WHERE role_code='NATIVETARGET'"));
    }

    @Test
    void managedAccountEditAndSoftDeletePreserveIdentityAndRevokeAccess() throws Exception {
        seed();
        var user = customer("softdeleteuser");
        long userId = ((UserPrincipal) user.getPrincipal()).id();
        long scope = Long.parseLong(sql("SELECT min(scope_id) FROM carriers"));
        sql("INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES(" + scope + "," + userId + ",'CUSTOMER')");
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
        assertEquals(
            "0",
            sql(
                "SELECT count(*) FROM (SELECT DISTINCT scope_id,user_id FROM auth_user_roles WHERE scope_id>0) memberships WHERE user_id=" +
                    userId
            )
        );
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
        call(401, "GET", "/api/v1/my-bookings", null, user);
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
        long carrier = id("carriers", other ? "VRBACNAM" : "VRMIENNAM");
        long scope = Long.parseLong(sql("SELECT scope_id FROM carriers WHERE id=" + carrier));
        long driver = id("carrier_drivers", other ? "DRVNORTH01" : "DRVSOUTH01");
        long vehicle = id("carrier_vehicles", other ? "VRNORTH01" : "VRSOUTH01");
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
                ",'DRIVER'); INSERT INTO auth_user_roles(scope_id,user_id,role_code) VALUES(" +
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
                categoryId("LOCATION", "VRTHUDUC"),
                categoryId("LOCATION", "VRDANANG"),
                categoryId("LOCATION", "VRHANOI")
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
            "INSERT INTO auth_role_functions(role_code,function_code,operation_codes) SELECT 'DRIVER',code,ARRAY[code] FROM auth_functions WHERE code IN('DRIVER_TRIP_VIEW','DRIVER_TRIP_READ','DRIVER_TRIP_UPDATE') ON CONFLICT DO NOTHING"
        );
        assignFixtureResources("DRIVER");
        long user = Long.parseLong(sql("SELECT user_id FROM carrier_drivers WHERE id=" + fixture.driver()));
        return actor(user, sql("SELECT username FROM auth_users WHERE id=" + user), "DRIVER");
    }

    private Authentication customer(String username) throws Exception {
        sql(
            "INSERT INTO auth_role_functions(role_code,function_code,operation_codes) SELECT 'CUSTOMER',code,ARRAY[code] FROM auth_functions WHERE code IN('MY_BOOKING_VIEW','MY_BOOKING_READ','MY_BOOKING_CREATE','MY_BOOKING_UPDATE') ON CONFLICT DO NOTHING"
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
        // Function fixture đã có endpoint tường minh ngay lúc setup; không cần một projection/marker thứ hai.
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
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'MY_BOOKING_READ') WHERE role_code='CUSTOMER'"
        );
        call(403, "GET", "/api/v1/my-bookings", null, buyer);
        call(403, "GET", "/api/v1/my-bookings/" + id, null, buyer);
        sql(
            "UPDATE auth_user_roles SET active=false WHERE scope_id=" +
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
        long middle = categoryId("LOCATION", "VRDANANG"),
            last = categoryId("LOCATION", "VRHANOI");
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
        var apiDocument = mvc
            .perform(request(HttpMethod.GET, "/v3/api-docs").with(authentication(admin)))
            .andReturn()
            .getResponse();
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
            call(200, "GET", path + "&carrierId=" + id("carriers", "VRBACNAM"), null, admin)
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
            sql("SELECT min(id) FROM category WHERE type='VALUE' AND ref_type='VEHICLE_TYPE' AND id<>" + type)
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
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'DRIVER_TRIP_VIEW') WHERE role_code='DRIVER'"
        );
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
            "UPDATE category SET name='Tên mới' WHERE code='VRDANANG'; UPDATE carrier_route_stops SET cumulative_fare=200000 WHERE route_id=" +
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
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'TRIP_VIEW') WHERE role_code='TRIP_CI'"
        );
        call(403, "GET", "/operations/trips", null, staff);
        call(200, "GET", "/api/v1/admin/trips", null, staff);
        sql(
            "UPDATE auth_user_roles SET active=false WHERE scope_id=" +
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
        long carrier = id("carriers", "VRMIENNAM"),
            first = categoryId("LOCATION", "VRTHUDUC"),
            middle = categoryId("LOCATION", "VRDANANG"),
            last = categoryId("LOCATION", "VRHANOI");
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
            "UPDATE category SET active=false WHERE id=(SELECT parent_id FROM category WHERE ref_type='LOCATION' AND id=" +
                first +
                ")"
        );
        call(409, "POST", "/api/v1/admin/routes", body.replace("TESTROUTE", "OFFPARENT"), admin);
        assertEquals("1", sql("SELECT count(*) FROM carrier_routes"));
    }

    @Test
    void routesFilterScopeAtCountIdPickerAndRespectIndependentView() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM"),
            other = id("carriers", "VRBACNAM"),
            first = categoryId("LOCATION", "VRTHUDUC"),
            middle = categoryId("LOCATION", "VRDANANG"),
            last = categoryId("LOCATION", "VRHANOI");
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
        sql(
            "UPDATE auth_role_functions SET operation_codes=array_remove(operation_codes,'ROUTE_VIEW') WHERE role_code='ROUTE_CI'"
        );
        call(403, "GET", "/operations/routes", null, staff);
        call(200, "GET", "/api/v1/admin/routes", null, staff);
        sql("UPDATE auth_user_roles SET active=false WHERE scope_id=" + scope);
        assertEquals(0, call(200, "GET", "/api/v1/admin/routes", null, staff).path("totalElements").asInt());
        call(404, "GET", "/api/v1/admin/routes/" + owned, null, staff);
    }

    @Test
    void routeStopFailureRollsBackParentAndExistingStopSet() throws Exception {
        seed();
        long carrier = id("carriers", "VRMIENNAM"),
            first = categoryId("LOCATION", "VRTHUDUC"),
            middle = categoryId("LOCATION", "VRDANANG"),
            last = categoryId("LOCATION", "VRHANOI");
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
