import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.postgresql.ds.PGSimpleDataSource;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.LegacyMigrationSql;
import vn.vietroute.common.jdbc.SchemaBootstrap;

/** Dựng đúng V012 trong DB Docker CI mới, không hạ cấp hoặc xóa dữ liệu của schema đang chạy. */
public final class LegacyViewUpgradeFixture {

    private LegacyViewUpgradeFixture() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_UPGRADE_DB_URL");
        if (!"jdbc:postgresql://vietroute-db-ci:5432/view_upgrade_ci".equals(url)) {
            throw new IllegalArgumentException("Chỉ DB nâng cấp Docker CI riêng");
        }
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser("vietroute_ci");
        source.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        var schema = new JdbcSchema("vietroute");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            new SchemaBootstrap(schema).prepare(connection);
            for (String version : List.of(
                "V001__auth_and_catalog.sql",
                "V002__vietroute_schema_and_indexes.sql",
                "V003__permissions_and_jobs.sql",
                "V004__media_assets.sql",
                "V005__api_request_journal.sql",
                "V006__system_audit.sql",
                "V007__configurable_access.sql",
                "V008__media_metadata.sql",
                "V009__activity_presence.sql",
                "V010__access_resource_inventory.sql",
                "V011__account_and_request_actor.sql",
                "V012__roles_profile_resources.sql"
            )) {
                byte[] bytes;
                try (var input = LegacyViewUpgradeFixture.class.getResourceAsStream("/db/migration/" + version)) {
                    if (input == null) throw new IllegalStateException(version);
                    bytes = input.readAllBytes();
                }
                try (var statement = connection.createStatement()) {
                    statement.execute(
                        LegacyMigrationSql.render(version, new String(bytes, StandardCharsets.UTF_8), schema)
                    );
                }
                try (
                    var insert = connection.prepareStatement(
                        "INSERT INTO vietroute_schema_history(version,checksum) VALUES (?,?)"
                    )
                ) {
                    insert.setString(1, version);
                    insert.setString(2, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                    insert.executeUpdate();
                }
            }
            try (
                var insert = connection.prepareStatement(
                    "INSERT INTO auth_users(id,username,password_hash,display_name,must_change_password) VALUES (1,'admin',?,'CI upgrade',false)"
                )
            ) {
                insert.setString(1, System.getenv("CI_UPGRADE_HASH"));
                insert.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO auth_roles(code,name_vi,name_en,permissions_initialized,permission_version) VALUES ('UPGRADE_CI','Nâng cấp','Upgrade',true,4),('LOOKALIKE_CI','Tên gần giống','Similar prefix',true,4)"
                );
                statement.executeUpdate(
                    "INSERT INTO auth_permissions(code,name_vi,name_en) VALUES ('CATALOG_READ','Đọc danh mục','Read catalog'),('CATALOGX_READ','Khác danh mục','Different resource')"
                );
                statement.executeUpdate(
                    "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('UPGRADE_CI','CATALOG_READ'),('LOOKALIKE_CI','CATALOGX_READ')"
                );
                statement.execute("SELECT setval(pg_get_serial_sequence('auth_users','id'),1,true)");
            }
            connection.commit();
        }
    }
}
