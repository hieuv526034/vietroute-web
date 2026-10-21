package vn.vietroute.common.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;
import java.util.Locale;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.common.jdbc.JdbcTransactions;

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
        transactions.execute(connection -> {
            // Khóa transaction được PostgreSQL tự giải phóng khi commit/rollback hoặc mất kết nối.
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(781205610)");
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS vietroute_schema_history (version VARCHAR(40) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)"
                );
            }
            migrate(connection, "V001__auth_and_catalog.sql");
            seedRoles(connection);
            seedAdmin(connection);
            seedCatalogs(connection);
            return null;
        });
        LOG.info("VietRoute database migration and bootstrap completed");
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

    private void seedRoles(Connection connection) throws SQLException {
        String[][] roles = {
            { "ADMIN", "Quản trị viên", "Administrator" },
            { "OPERATOR", "Quản lý nhà xe", "Operator" },
            { "DRIVER", "Tài xế", "Driver" },
            { "CUSTOMER", "Khách hàng", "Customer" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_roles(code,name_vi,name_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING"
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
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM auth_users WHERE username=?")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_users(username,password_hash,display_name) VALUES (?,?,?) RETURNING id"
            )
        ) {
            insert.setString(1, username);
            insert.setString(2, passwords.encode(settings.getBootstrapPassword()));
            insert.setString(3, "Quản trị viên");
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                try (
                    PreparedStatement role = connection.prepareStatement(
                        "INSERT INTO auth_user_roles(user_id,role_code) VALUES (?, 'ADMIN')"
                    )
                ) {
                    role.setLong(1, result.getLong(1));
                    role.executeUpdate();
                }
            }
        }
    }

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
                "INSERT INTO catalog_items(type,code,name_vi,name_en,sort_order) VALUES (?,?,?,?,?) ON CONFLICT (type,code) DO NOTHING"
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
