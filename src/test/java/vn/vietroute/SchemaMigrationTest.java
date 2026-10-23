package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.LegacyMigrationSql;

/** Kiểm compatibility adapter chỉ ánh xạ schema của V002/V003, không đổi byte/checksum nguồn hoặc migration mới. */
class SchemaMigrationTest {

    /** History lưu nguyên tên file trong VARCHAR(40); phát hiện tên quá dài trước khi startup chạm DB. */
    @Test
    void migrationNamesFitTheExistingHistoryColumn() throws Exception {
        Path directory = Path.of(getClass().getResource("/db/migration").toURI());
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".sql")) {
                    assertTrue(name.length() <= 40, "Tên migration vượt giới hạn history: " + name);
                }
            }
        }
    }

    @Test
    void legacyDdlTargetsConfiguredSchemaIncludingSqlKeywordNames() throws Exception {
        for (String version : new String[] {
            "V002__vietroute_schema_and_indexes.sql",
            "V003__permissions_and_jobs.sql",
        }) {
            try (var input = getClass().getResourceAsStream("/db/migration/" + version)) {
                String original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                String rendered = LegacyMigrationSql.render(version, original, new JdbcSchema("select"));
                assertFalse(rendered.contains("vietroute."));
                assertTrue(rendered.contains("\"select\"."));
                assertTrue(original.contains("vietroute."));
                if (version.startsWith("V002")) {
                    assertTrue(rendered.contains("ALTER TABLE public.%I SET SCHEMA \"select\""));
                }
            }
        }
    }

    @Test
    void newMigrationIsNotGloballyRewrittenAndHistoryTableNameIsPreserved() {
        String sql = "SELECT 'vietroute', 'vietroute_schema_history'";
        assertEquals(sql, LegacyMigrationSql.render("V004__new_feature.sql", sql, new JdbcSchema("custom")));
        assertEquals(
            "SELECT '\"custom\"', 'vietroute_schema_history'",
            LegacyMigrationSql.render("V003__permissions_and_jobs.sql", sql, new JdbcSchema("custom"))
        );
    }
}
