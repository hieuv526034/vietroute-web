import java.sql.Connection;
import java.util.List;
import java.util.Set;
import org.postgresql.ds.PGSimpleDataSource;
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.repository.AccessFeatureRepository;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.security.AccessPermissionDefinitions;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Regression DAO thực trên PostgreSQL Docker CI; fixture vượt quota để kiểm lỗi dữ liệu ngoài API, luôn dọn sau kiểm. */
public final class AccessDefinitionInspector {

    private AccessDefinitionInspector() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_GUARD_DB_URL");
        if (!url.startsWith("jdbc:postgresql://vietroute-db-ci:5432/")) throw new IllegalArgumentException(
            "Chỉ chạy DB Docker CI"
        );
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(System.getenv("CI_GUARD_DB_USER"));
        source.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        var connections = new JdbcConnectionFactory(source, new JdbcSchema(System.getenv("CI_GUARD_DB_SCHEMA")));
        var features = new AccessFeatureRepository(connections);
        var definitions = new AccessPermissionDefinitions();
        var registry = new AccessRegistryRepository(connections, new AccessSettings(key -> null), definitions);
        try {
            long user;
            try (var connection = connections.open(); var query = connection.createStatement()) {
                try (
                    var row = query.executeQuery(
                        "INSERT INTO auth_users(username,password_hash,display_name,active,must_change_password,email_verified_at) VALUES ('definitionci','!CI_DISABLED','CI fixture',true,false,CURRENT_TIMESTAMP) RETURNING id"
                    )
                ) {
                    row.next();
                    user = row.getLong(1);
                }
                query.executeUpdate(
                    "INSERT INTO auth_roles(code,name_vi,name_en) SELECT 'DEFINITION_'||n||'_CI','CI fixture','CI fixture' FROM generate_series(1,5) n"
                );
                try (
                    var roles = connection.prepareStatement(
                        "INSERT INTO auth_user_roles(user_id,role_code) SELECT ?,code FROM auth_roles WHERE code LIKE 'DEFINITION_%_CI'"
                    )
                ) {
                    roles.setLong(1, user);
                    roles.executeUpdate();
                }
                connection.commit();
            }
            var inventory = List.of(
                new AccessResourceResponse(
                    "ENDPOINT",
                    "GET /fixture",
                    "Fixture#read",
                    "",
                    false,
                    true,
                    null,
                    null,
                    "DEFINITION",
                    List.of("DEFINITION_DETAIL")
                )
            );
            var serviceCodes = Set.of("DEFINITION_CREATE");
            definitions.remember(inventory, serviceCodes);
            try (var connection = connections.open()) {
                features.synchronize(connection, inventory, serviceCodes);
                try (var query = connection.createStatement()) {
                    query.executeUpdate(
                        "UPDATE auth_permissions SET active=false,name_vi='Tên đã sửa',name_en='Edited name' WHERE code='DEFINITION_CREATE'"
                    );
                }
                features.synchronize(connection, inventory, serviceCodes);
                var service = registry.find(connection, "permissions", "DEFINITION_CREATE");
                var http = registry.find(connection, "permissions", "DEFINITION_DETAIL");
                require(
                    service.builtIn() && http.builtIn() && service.active(),
                    "Metadata HTTP/service và sync phải thống nhất"
                );
                require(
                    service.nameVi().equals("Tên đã sửa") && service.nameEn().equals("Edited name"),
                    "Sync phải giữ tên quản trị đã sửa"
                );
                require(
                    http.nameVi().equals("DEFINITION_DETAIL") && http.nameEn().equals("DEFINITION_DETAIL"),
                    "Thiếu key phải hiện mã tường minh"
                );
                try (
                    var query = connection.createStatement();
                    var rows = query.executeQuery(
                        "SELECT count(*) FILTER (WHERE role_code='ADMIN'),count(*) FILTER (WHERE role_code<>'ADMIN') FROM auth_role_permissions WHERE permission_code IN ('DEFINITION_CREATE','DEFINITION_DETAIL')"
                    )
                ) {
                    rows.next();
                    require(
                        rows.getInt(1) == 2 && rows.getInt(2) == 0,
                        "Sync chỉ cấp ADMIN, không tự thêm role thường"
                    );
                }
                try (var query = connection.createStatement()) {
                    query.executeUpdate(
                        "INSERT INTO auth_permissions(code,name_vi,name_en) SELECT 'DEFINITION_'||lpad(n::text,4,'0')||'_READ','CI fixture','CI fixture' FROM generate_series(1,2049) n"
                    );
                    query.executeUpdate(
                        "INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'DEFINITION_'||((n-1)/512+1)||'_CI','DEFINITION_'||lpad(n::text,4,'0')||'_READ' FROM generate_series(1,513) n"
                    );
                    query.executeUpdate(
                        "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('DEFINITION_5_CI','DEFINITION_0001_READ')"
                    );
                }
                connection.commit();
            }
            require(features.effective(user).sources().size() == 513, "Không được cắt ở 512 quyền");
            require(
                features.effective(user).sources().get("DEFINITION_0513_READ").equals(Set.of("DEFINITION_2_CI")),
                "Phải giữ nguồn từ role thứ hai"
            );
            try (var connection = connections.open(); var query = connection.createStatement()) {
                query.executeUpdate(
                    "INSERT INTO auth_role_permissions(role_code,permission_code) SELECT 'DEFINITION_'||((n-1)/512+1)||'_CI','DEFINITION_'||lpad(n::text,4,'0')||'_READ' FROM generate_series(514,2048) n"
                );
                connection.commit();
            }
            require(features.effective(user).sources().size() == 2048, "Phải đọc đủ tới trần registry");
            require(
                features
                    .effective(user)
                    .sources()
                    .get("DEFINITION_0001_READ")
                    .equals(Set.of("DEFINITION_1_CI", "DEFINITION_5_CI")),
                "Phải gom đủ mọi role cùng cấp một quyền"
            );
            try (
                var connection = connections.open();
                var query = connection.prepareStatement("UPDATE auth_users SET must_change_password=true WHERE id=?")
            ) {
                query.setLong(1, user);
                query.executeUpdate();
                connection.commit();
            }
            require(features.effective(user).sources().isEmpty(), "Mật khẩu tạm không có quyền hiệu lực");
            try (var connection = connections.open(); var query = connection.createStatement()) {
                query.executeUpdate("UPDATE auth_users SET must_change_password=false WHERE username='definitionci'");
                query.executeUpdate(
                    "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES ('DEFINITION_5_CI','DEFINITION_2049_READ')"
                );
                connection.commit();
            }
            try {
                features.effective(user);
                throw new IllegalStateException("Vượt trần phải báo lỗi thay vì trả danh sách thiếu");
            } catch (BusinessException failure) {
                require(failure.getErrorCode() == ErrorCode.ACCESS_CAPACITY_REACHED, "Phải trả COM016");
            }
            System.out.println(
                "Access definitions: HTTP/service builtIn, preserved names, ADMIN-only sync; effective 513/2048 complete, 2049 rejected COM016."
            );
        } finally {
            try (var connection = connections.open()) {
                cleanup(connection);
                connection.commit();
            }
        }
    }

    /** Fixture chỉ dùng prefix riêng, xóa con trước cha để giữ toàn bộ FK production. */
    private static void cleanup(Connection connection) throws Exception {
        try (var query = connection.createStatement()) {
            query.executeUpdate(
                "DELETE FROM auth_role_permissions WHERE permission_code LIKE 'DEFINITION_%' OR role_code LIKE 'DEFINITION_%_CI'"
            );
            query.executeUpdate("DELETE FROM auth_user_roles WHERE role_code LIKE 'DEFINITION_%_CI'");
            query.executeUpdate("DELETE FROM auth_permissions WHERE code LIKE 'DEFINITION_%'");
            query.executeUpdate("DELETE FROM auth_roles WHERE code LIKE 'DEFINITION_%_CI'");
            query.executeUpdate("DELETE FROM auth_users WHERE username='definitionci'");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
