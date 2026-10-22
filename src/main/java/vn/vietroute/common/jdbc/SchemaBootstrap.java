package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Chuẩn bị schema/history dưới advisory lock của initializer, tuyệt đối không tự đổi tên schema đang phục vụ. */
public final class SchemaBootstrap {

    private static final String HISTORY = "vietroute_schema_history";

    private static final Set<String> LEGACY_TABLES = Set.of(
        "auth_roles",
        "auth_users",
        "auth_user_roles",
        "catalog_items"
    );

    private final JdbcSchema schema;

    public SchemaBootstrap(JdbcSchema schema) {
        this.schema = schema;
    }

    /**
     * Chỉ một history của ứng dụng trong database. Nếu gõ nhầm schema khi dữ liệu đã nằm ở nơi khác,
     * dừng trước CREATE/seed thay vì tạo admin/bảng rỗng song song. Không đoán và di chuyển dữ liệu đang chạy.
     */
    public void prepare(Connection connection) throws SQLException {
        String installed = null;
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT n.nspname FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE c.relname=? AND c.relkind IN ('r','p')
                    AND left(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema'
                """
            )
        ) {
            query.setString(1, HISTORY);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (installed != null) {
                        throw new IllegalStateException(
                            "Multiple application migration histories; resolve schema ownership before startup"
                        );
                    }
                    installed = rows.getString(1);
                }
            }
        }
        if (installed != null && !installed.equals(schema.name()) && !installed.equals("public")) {
            throw new IllegalStateException(
                "Application history is in another schema; stop all instances and rename the existing schema before changing " +
                    Env.DB_SCHEMA.key()
            );
        }
        if (installed == null && hasLegacyTables(connection)) {
            throw new IllegalStateException("Legacy application tables are missing migration history");
        }
        if ("public".equals(installed) && hasTargetTables(connection)) {
            throw new IllegalStateException(
                "Target schema contains application tables while legacy public history exists"
            );
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema.quoted());
            if ("public".equals(installed)) {
                statement.execute("ALTER TABLE public." + HISTORY + " SET SCHEMA " + schema.quoted());
            }
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                    schema.quoted() +
                    "." +
                    HISTORY +
                    " (version VARCHAR(40) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)"
            );
        }
        // Factory đã đặt path trước khi schema tồn tại; đặt lại tại ranh giới bootstrap để đọc rõ luồng.
        schema.select(connection);
    }

    private static boolean hasLegacyTables(Connection connection) throws SQLException {
        return hasTables(connection, "public");
    }

    private boolean hasTargetTables(Connection connection) throws SQLException {
        return hasTables(connection, schema.name());
    }

    private static boolean hasTables(Connection connection, String namespace) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT c.relname FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname=? AND c.relkind IN ('r','p')
                """
            )
        ) {
            query.setString(1, namespace);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (LEGACY_TABLES.contains(rows.getString(1))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
