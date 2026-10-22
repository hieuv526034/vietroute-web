package vn.vietroute.common.jdbc;

import java.util.Set;

/**
 * Adapter chỉ cho hai migration lịch sử đã cố định schema trước khi có cấu hình.
 * Checksum vẫn tính trên byte gốc; chỉ bản SQL thực thi trong RAM được ánh xạ identifier.
 * Không sửa literal nghiệp vụ hoặc áp dụng replace toàn cục lên các migration mới.
 */
public final class LegacyMigrationSql {

    private static final Set<String> FIXED_SCHEMA_VERSIONS = Set.of(
        "V002__vietroute_schema_and_indexes.sql",
        "V003__permissions_and_jobs.sql"
    );

    private LegacyMigrationSql() {}

    public static String render(String version, String script, JdbcSchema schema) {
        return FIXED_SCHEMA_VERSIONS.contains(version) ? script.replaceAll("\\bvietroute\\b", schema.quoted()) : script;
    }
}
