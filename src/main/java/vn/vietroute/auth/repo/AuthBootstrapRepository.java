package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.Permission;

/** Dữ liệu nền theo code duy nhất; SQL init ở repository, không query theo từng bản ghi. */
@Repository
public class AuthBootstrapRepository {

    /** Caller giữ transaction migration; chạy lại không đổi dữ liệu người quản trị đã sửa. */
    public void seed(Connection connection) throws SQLException {
        seedRoles(connection);
        seedPermissions(connection);
        seedCatalogs(connection);
    }

    /** Batch registry; cấp ADMIN bằng INSERT SELECT, role thường không nhận quyền tự động. */
    private void seedPermissions(Connection connection) throws SQLException {
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        try (
            var insert = connection.prepareStatement(
                "INSERT INTO auth_functions(code,name_vi,name_en,operation_codes,source_managed) VALUES (?,?,?,ARRAY[?],true) ON CONFLICT(code) DO NOTHING"
            )
        ) {
            for (var permission : Permission.values()) {
                insert.setString(1, permission.name());
                insert.setString(2, vi.getString(permission.messageKey()));
                insert.setString(3, en.getString(permission.messageKey()));
                insert.setString(4, permission.name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (
            var insert = connection.prepareStatement(
                "INSERT INTO auth_role_functions(role_code,function_code,operation_codes) SELECT 'ADMIN',code,operation_codes FROM auth_functions WHERE active AND NOT deleted ON CONFLICT DO NOTHING"
            )
        ) {
            insert.executeUpdate();
        }
        try (
            var update = connection.prepareStatement(
                "UPDATE auth_roles SET permissions_initialized=true WHERE NOT permissions_initialized"
            )
        ) {
            update.executeUpdate();
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
