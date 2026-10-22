package vn.vietroute.common.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.common.jdbc.JdbcSchemaValidator;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.Job;

/** Migration/seed chạy theo cờ môi trường; tuyệt đối không chạy từ bước đóng gói Maven. */
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements InitializingBean {

    private static final Logger LOG = Logger.getLogger(DatabaseInitializer.class.getName());

    private final AppSettings settings;

    private final JdbcTransactions transactions;

    private final PasswordEncoder passwords;

    @Override
    public void afterPropertiesSet() {
        if (!settings.isDatabaseInitEnabled()) {
            return;
        }
        transactions.execute(this::initialize);
        LOG.info("VietRoute database migration and bootstrap completed");
    }

    /** Một transaction bao trọn chuyển schema, migration, kiểm tra index và seed; lỗi ở đâu rollback ở đó. */
    private Void initialize(Connection connection) throws SQLException {
        // PostgreSQL tự giải phóng advisory lock khi commit/rollback hoặc mất kết nối.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(781205610)");
        }
        prepareSchema(connection);
        migrate(connection, "V001__auth_and_catalog.sql");
        migrate(connection, "V002__vietroute_schema_and_indexes.sql");
        migrate(connection, "V003__permissions_and_jobs.sql");
        JdbcSchemaValidator.verify(
            connection,
            UserAccount.class,
            CatalogItem.class,
            Job.class,
            Role.class,
            Permission.class
        );
        seedRoles(connection);
        seedPermissions(connection);
        seedAdmin(connection);
        seedCatalogs(connection);
        return null;
    }

    /** Seed registry quyền; role thường chỉ nhận mặc định một lần, không phục hồi quyền quản trị đã thu hồi. */
    private void seedPermissions(Connection connection) throws SQLException {
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.auth_permissions(code,name_vi,name_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING"
            )
        ) {
            for (Permission permission : Permission.values()) {
                insert.setString(1, permission.name());
                insert.setString(2, vi.getString(permission.messageKey()));
                insert.setString(3, en.getString(permission.messageKey()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        for (Role role : Role.values()) {
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT permissions_initialized FROM vietroute.auth_roles WHERE code=?"
                )
            ) {
                query.setString(1, role.name());
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    if (row.getBoolean(1) && role != Role.ADMIN) {
                        continue;
                    }
                }
            }
            try (
                PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO vietroute.auth_role_permissions(role_code,permission_code) VALUES (?,?) ON CONFLICT DO NOTHING"
                )
            ) {
                for (Permission permission : Permission.values()) {
                    if (permission.grantedInitially(role)) {
                        insert.setString(1, role.name());
                        insert.setString(2, permission.name());
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            try (
                PreparedStatement update = connection.prepareStatement(
                    "UPDATE vietroute.auth_roles SET permissions_initialized=true WHERE code=?"
                )
            ) {
                update.setString(1, role.name());
                update.executeUpdate();
            }
        }
    }

    /**
     * Chuyển duy nhất history cũ để tiếp tục kiểm tra checksum V001, không chạy lại trên dữ liệu cũ.
     * Nếu tồn tại hai history hoặc bảng cũ không rõ nguồn gốc, dừng thay vì đoán và tạo dữ liệu song song.
     */
    private void prepareSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS vietroute");
            statement.execute(
                """
                DO $history$
                BEGIN
                    IF to_regclass('public.vietroute_schema_history') IS NOT NULL THEN
                        IF to_regclass('vietroute.vietroute_schema_history') IS NOT NULL THEN
                            RAISE EXCEPTION 'Application migration history exists in both schemas';
                        END IF;
                        ALTER TABLE public.vietroute_schema_history SET SCHEMA vietroute;
                    ELSIF to_regclass('vietroute.vietroute_schema_history') IS NULL
                        AND (to_regclass('public.auth_users') IS NOT NULL
                            OR to_regclass('public.auth_roles') IS NOT NULL
                            OR to_regclass('public.auth_user_roles') IS NOT NULL
                            OR to_regclass('public.catalog_items') IS NOT NULL) THEN
                        RAISE EXCEPTION 'Legacy application tables are missing migration history';
                    END IF;
                END
                $history$;
                """
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS vietroute.vietroute_schema_history (version VARCHAR(40) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)"
            );
            // V001 giữ nguyên byte/checksum; trên DB mới, các tên chưa có schema sẽ được tạo vào vietroute.
            statement.execute("SET LOCAL search_path TO vietroute");
        }
    }

    /** Checksum ngăn sửa âm thầm migration đã chạy; thay schema phải tạo version mới. */
    private void migrate(Connection connection, String version) throws SQLException {
        byte[] script;
        String checksum;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/" + version)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + version);
            }
            script = input.readAllBytes();
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load migration " + version, exception);
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT checksum FROM vietroute.vietroute_schema_history WHERE version=?"
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
                "INSERT INTO vietroute.vietroute_schema_history(version, checksum) VALUES (?,?)"
            )
        ) {
            insert.setString(1, version);
            insert.setString(2, checksum);
            insert.executeUpdate();
        }
    }

    /** Seed quyền ổn định theo code; chạy lại không ghi đè tên hay dữ liệu người dùng đã chỉnh. */
    private void seedRoles(Connection connection) throws SQLException {
        String[][] roles = {
            { "ADMIN", "Quản trị viên", "Administrator" },
            { "OPERATOR", "Quản lý nhà xe", "Operator" },
            { "DRIVER", "Tài xế", "Driver" },
            { "CUSTOMER", "Khách hàng", "Customer" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.auth_roles(code,name_vi,name_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING"
            )
        ) {
            for (String[] role : roles) {
                for (int i = 0; i < role.length; i++) {
                    insert.setString(i + 1, role[i]);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Chỉ tạo tài khoản khi chưa có; không reset hash, trạng thái hay gán thêm quyền vào user đã tồn tại. */
    private void seedAdmin(Connection connection) throws SQLException {
        String username = settings.getBootstrapUsername().toLowerCase(Locale.ROOT);
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM vietroute.auth_users WHERE username=?"
            )
        ) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.auth_users(username,password_hash,display_name) VALUES (?,?,?) RETURNING id"
            )
        ) {
            insert.setString(1, username);
            insert.setString(2, passwords.encode(settings.getBootstrapPassword()));
            insert.setString(3, "Quản trị viên");
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                try (
                    PreparedStatement role = connection.prepareStatement(
                        "INSERT INTO vietroute.auth_user_roles(user_id,role_code) VALUES (?, 'ADMIN')"
                    )
                ) {
                    role.setLong(1, result.getLong(1));
                    role.executeUpdate();
                }
            }
        }
    }

    /** Seed danh mục nền bằng UNIQUE(type, code); không thêm dữ liệu chuyến/đơn giả. */
    private void seedCatalogs(Connection connection) throws SQLException {
        String[][] values = {
            { "VEHICLE_TYPE", "SEAT", "Ghế ngồi", "Seated coach" },
            { "VEHICLE_TYPE", "SLEEPER", "Giường nằm", "Sleeper coach" },
            { "VEHICLE_TYPE", "LIMOUSINE", "Limousine", "Limousine" },
            { "BOOKING_SOURCE", "WEB", "Đặt trên website", "Website" },
            { "BOOKING_SOURCE", "PHONE", "Đặt qua điện thoại", "Phone" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.catalog_items(type,code,name_vi,name_en,sort_order) VALUES (?,?,?,?,?) ON CONFLICT (type,code) DO NOTHING"
            )
        ) {
            for (int i = 0; i < values.length; i++) {
                for (int j = 0; j < values[i].length; j++) {
                    insert.setString(j + 1, values[i][j]);
                }
                insert.setInt(5, i);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
