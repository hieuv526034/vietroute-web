package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Lưu tập tài nguyên tường minh trong transaction gán role; không tự mở rộng theo API mới. */
@Repository
public class RoleResourceRepository {

    public record Grant(Role role, boolean initialized, Set<Permission> permissions, Set<String> targets) {}

    /** Gán nhiều role đọc một snapshot tập hợp, kể cả permission tắt và marker native rỗng để không bỏ qua trần ủy quyền. */
    public List<Grant> grants(Connection connection, Set<Role> roles) throws SQLException {
        if (roles.isEmpty()) return List.of();
        var codes = connection.createArrayOf(
            "text",
            roles
                .stream()
                .map(Role::name)
                .toArray(String[]::new)
        );
        var result = new ArrayList<Grant>();
        try (
            var query = connection.prepareStatement(
                """
                SELECT r.code,EXISTS(SELECT 1 FROM auth_role_resource_sets rs WHERE rs.role_code=r.code) AS initialized,
                    ARRAY(SELECT rp.permission_code FROM auth_role_permissions rp WHERE rp.role_code=r.code ORDER BY rp.permission_code) AS permission_codes,
                    ARRAY(SELECT rr.target FROM auth_role_resources rr WHERE rr.role_code=r.code ORDER BY rr.target) AS targets
                FROM auth_roles r WHERE r.code=ANY(?) AND NOT r.deleted ORDER BY r.code
                """
            )
        ) {
            query.setArray(1, codes);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    result.add(
                        new Grant(
                            Role.valueOf(rows.getString("code")),
                            rows.getBoolean("initialized"),
                            AccountAccessSql.permissions(rows),
                            AccountAccessSql.strings(rows, "targets")
                        )
                    );
            }
            if (result.size() != roles.size()) throw new BusinessException(ErrorCode.NOT_FOUND);
            return List.copyOf(result);
        } finally {
            codes.free();
        }
    }

    /** Chuyển các role cũ đúng một lần sau khi inventory sẵn sàng; startup sau không tự cấp route mới. */
    public static void initializeLegacy(Connection connection) throws SQLException {
        try (
            var query = connection.prepareStatement(
                """
                WITH added AS (
                    INSERT INTO auth_role_resource_sets(role_code)
                    SELECT code FROM auth_roles WHERE NOT deleted ON CONFLICT DO NOTHING RETURNING role_code
                )
                INSERT INTO auth_role_resources(role_code,kind,target)
                SELECT DISTINCT a.role_code,r.kind,r.target FROM added a CROSS JOIN auth_access_resources r
                WHERE NOT r.public_route AND r.declared_permissions<>''
                  AND EXISTS(SELECT 1 FROM auth_role_permissions p WHERE p.role_code=a.role_code
                    AND p.permission_code=ANY(string_to_array(r.declared_permissions,',')))
                """
            )
        ) {
            query.executeUpdate();
        }
    }

    public boolean initialized(Connection connection, Role role) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM auth_role_resource_sets WHERE role_code=?")) {
            query.setString(1, role.name());
            try (var row = query.executeQuery()) {
                return row.next();
            }
        }
    }

    public Set<String> targets(Connection connection, Role role) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (
            var query = connection.prepareStatement(
                "SELECT target FROM auth_role_resources WHERE role_code=? ORDER BY target"
            )
        ) {
            query.setString(1, role.name());
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return Set.copyOf(result);
    }

    /** Kể cả tập rỗng vẫn ghi marker: không fallback về projection hành động đã được sinh ra trước đó. */
    public void replace(Connection connection, Role role, Set<AccessResourceResponse> resources, long actor)
        throws SQLException {
        mark(connection, role.name(), actor);
        try (var query = connection.prepareStatement("DELETE FROM auth_role_resources WHERE role_code=?")) {
            query.setString(1, role.name());
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_role_resources(role_code,kind,target,created_by,updated_by) VALUES (?,?,?,?,?)"
            )
        ) {
            for (var resource : resources) {
                query.setString(1, role.name());
                query.setString(2, resource.kind());
                query.setString(3, resource.target());
                query.setLong(4, actor);
                query.setLong(5, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    /** Role mới bắt đầu không tài nguyên, tách biệt với đường chuyển đổi role legacy. */
    public static void mark(Connection connection, String role, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_role_resource_sets(role_code,created_by,updated_by) VALUES (?,?,?) ON CONFLICT(role_code) DO NOTHING"
            )
        ) {
            query.setString(1, role);
            query.setLong(2, actor);
            query.setLong(3, actor);
            query.executeUpdate();
        }
    }
}
