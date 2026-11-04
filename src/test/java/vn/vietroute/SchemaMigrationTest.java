package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.initializer.MigrationCatalog;

/** Baseline theo khối: đủ resource, phụ thuộc, checksum và không tạo bảng đã bỏ. */
class SchemaMigrationTest {

    @Test
    void migrationNamesFitTheExistingHistoryColumn() {
        for (String version : MigrationCatalog.versions()) assertTrue(version.length() <= 40, version);
    }

    /** PK/UNIQUE có trước actor system và FK liên cụm; không dựa thứ tự thư mục. */
    @Test
    void manifestKeepsCrossModuleDependenciesExplicit() {
        assertEquals(
            List.of(
                "auth/001_schema.sql",
                "catalog/001_schema.sql",
                "carrier/001_schema.sql",
                "trip/001_schema.sql",
                "booking/001_schema.sql",
                "common/001_schema.sql",
                "foundation/001_system_account.sql",
                "foundation/002_references.sql",
                "catalog/002_category.sql",
                "catalog/003_location_wards.sql"
            ),
            MigrationCatalog.versions()
        );
    }

    /** Snapshot khóa baseline mới; thay schema tiếp theo phải thêm bước. */
    @Test
    void baselineChecksumsArePinned() throws Exception {
        Properties expected = new Properties();
        try (var input = getClass().getResourceAsStream("/db/migration-checksums.properties")) {
            expected.load(input);
        }
        assertEquals(Set.copyOf(MigrationCatalog.versions()), expected.stringPropertyNames());
        for (String version : expected.stringPropertyNames()) {
            String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(MigrationCatalog.read(version))
            );
            assertEquals(expected.getProperty(version), checksum, version);
        }
    }

    @Test
    void manifestMatchesEveryPackagedMigrationExactlyOnce() throws Exception {
        Path root = Path.of(getClass().getResource("/db/migration").toURI());
        Set<String> packaged = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.toString().endsWith(".sql")) packaged.add(
                    "/db/migration/" + root.relativize(path).toString().replace('\\', '/')
                );
            }
        }
        Set<String> listed = new HashSet<>();
        for (String version : MigrationCatalog.versions()) assertTrue(listed.add(MigrationCatalog.resource(version)));
        assertEquals(10, listed.size());
        assertEquals(packaged, listed);
    }

    /** Không cần rewrite schema hoặc dựng lại bảng GPS/provider/quyền action đã nghỉ. */
    @Test
    void baselineUsesConfiguredSchemaAndOnlyCurrentTables() {
        for (String version : MigrationCatalog.versions()) {
            String sql = new String(MigrationCatalog.read(version), StandardCharsets.UTF_8);
            for (String retired : List.of(
                "trip_tracking",
                "trip_locations",
                "media_assets",
                "system_jobs",
                "system_visitor_presence",
                "auth_permissions",
                "auth_access_policies"
            ))
                assertFalse(sql.contains(retired), version + ": " + retired);
            assertFalse(sql.contains("vietroute."), version);
            assertFalse(sql.contains("public."), version);
            assertFalse(sql.matches("(?s).*DROP\\s+(?:TABLE|SCHEMA)[^;]*CASCADE\\s*;.*"), version);
        }
    }

    @Test
    void tripProgressRemainsWithoutDeviceTracking() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/vn/vietroute/business/tracking")));
        for (String file : new String[] { "trips.js", "driver-trips.js", "bookings.js" }) {
            String source = Files.readString(Path.of("src/main/webapp/assets/js").resolve(file));
            assertFalse(source.contains("VietRouteTracking"), file);
            assertFalse(source.contains("/tracking"), file);
        }
        String sql = new String(MigrationCatalog.read("trip/001_schema.sql"), StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS trip_stops"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS trip_events"));
    }
}
