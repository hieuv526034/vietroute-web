package vn.vietroute.common.initializer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.repo.custom.AccessChangeCustomRepo;
import vn.vietroute.business.catalog.repo.custom.CategoryCustomRepo;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Migration/seed chạy theo cờ môi trường; tuyệt đối không chạy từ bước đóng gói Maven. */
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements InitializingBean {

    private static final String HISTORY = "vietroute_schema_history";

    private static final Logger LOG = Logger.getLogger(DatabaseInitializer.class.getName());

    private final AppSettings settings;

    private final JdbcTransactions transactions;

    private final AuthInitializer auth;

    private final JdbcSchema schema;

    private final CategoryCustomRepo catalogs;

    @Override
    public void afterPropertiesSet() {
        if (!settings.isDatabaseInitEnabled()) {
            return;
        }
        transactions.execute(this::initialize);
        LOG.info("VietRoute database migration and bootstrap completed");
    }

    /** Một transaction bao trọn chuyển schema, migration có checksum và seed; lỗi ở đâu rollback ở đó. */
    private Void initialize(Connection connection) throws SQLException {
        // PostgreSQL tự giải phóng advisory lock khi commit/rollback hoặc mất kết nối.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT PG_ADVISORY_XACT_LOCK(781205610)");
        }
        prepareSchema(connection);
        // Chỉ nhận baseline hiện hành; đổi baseline là thao tác bảo trì ngoài runtime, không tự reset history.
        Set<String> installed = new HashSet<>();
        try (
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT version FROM vietroute_schema_history")
        ) {
            while (rows.next()) installed.add(rows.getString(1));
        }
        Set<String> known = new HashSet<>(MigrationCatalog.versions());
        if (!known.containsAll(installed)) throw new IllegalStateException(
            "Database baseline differs; stop application nodes, back up and run rebaseline-database.py"
        );
        for (String version : MigrationCatalog.versions()) {
            migrate(connection, version);
        }
        auth.initialize(connection);
        AccessChangeCustomRepo.cleanup(connection);
        catalogs.seedCatalogs(connection);
        return null;
    }

    /** Checksum ngăn sửa âm thầm migration đã chạy; thay schema phải tạo version mới. */
    private void migrate(Connection connection, String version) throws SQLException {
        byte[] script;
        String checksum;
        try {
            script = MigrationCatalog.read(version);
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load migration " + version, exception);
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT checksum FROM vietroute_schema_history WHERE version=?"
            )
        ) {
            query.setString(1, version);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    if (!checksum.equals(result.getString(1))) {
                        throw new IllegalStateException("Applied migration checksum changed: " + version);
                    }
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(new String(script, StandardCharsets.UTF_8));
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute_schema_history(version, checksum) VALUES (?,?)"
            )
        ) {
            insert.setString(1, version);
            insert.setString(2, checksum);
            insert.executeUpdate();
        }
    }

    /**
     * Chỉ một history của ứng dụng trong database. Nếu gõ nhầm schema khi dữ liệu đã nằm ở nơi khác,
     * dừng trước CREATE/seed thay vì tạo admin/bảng rỗng song song. Không đoán và di chuyển dữ liệu đang chạy.
     */
    private void prepareSchema(Connection connection) throws SQLException {
        String installed = null;
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT n.nspname FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE c.relname=? AND c.relkind IN ('r','p')
                    AND LEFT(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema'
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
        if (installed != null && !installed.equals(schema.name())) {
            throw new IllegalStateException(
                "Application history is in another schema; stop all instances and rename the existing schema before changing " +
                    Env.DB_SCHEMA.key()
            );
        }
        if (installed == null && hasTargetTables(connection)) {
            throw new IllegalStateException("Application tables are missing migration history; restore before startup");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema.quoted());
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

    private boolean hasTargetTables(Connection connection) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT c.relname FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname=? AND c.relkind IN ('r','p')
                """
            )
        ) {
            query.setString(1, schema.name());
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }
}
