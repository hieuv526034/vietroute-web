package vn.vietroute.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;

/** Projection dùng chung cho alias u: một snapshot SQL, không nhân bản cột user theo từng mapping role/quyền. */
final class AccountAccessSql {

    static final String AUTHORITIES = """
    , ARRAY(SELECT r.code FROM auth_user_roles ur
            JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
            WHERE ur.user_id=u.id ORDER BY r.code) AS role_codes
    , ARRAY(SELECT DISTINCT p.code FROM auth_user_roles ur
            JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
            JOIN auth_role_permissions rp ON rp.role_code=r.code
            JOIN auth_permissions p ON p.code=rp.permission_code AND p.active
            WHERE ur.user_id=u.id ORDER BY p.code) AS permission_codes
    """;

    static final String ARRAYS =
        AUTHORITIES +
        """
        , ARRAY(SELECT DISTINCT rr.target FROM auth_user_roles ur
                JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
                JOIN auth_role_resources rr ON rr.role_code=r.code
                WHERE ur.user_id=u.id ORDER BY rr.target) AS resource_targets
        , ARRAY(SELECT DISTINCT p.code FROM auth_user_roles ur
                JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
                JOIN auth_role_permissions rp ON rp.role_code=r.code
                JOIN auth_permissions p ON p.code=rp.permission_code AND p.active
                WHERE ur.user_id=u.id AND NOT EXISTS
                    (SELECT 1 FROM auth_role_resource_sets rs WHERE rs.role_code=r.code)
                ORDER BY p.code) AS legacy_permission_codes
        """;

    private AccountAccessSql() {}

    static Set<Role> roles(ResultSet row) throws SQLException {
        return strings(row, "role_codes").stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    static Set<Permission> permissions(ResultSet row) throws SQLException {
        return strings(row, "permission_codes")
            .stream()
            .map(Permission::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Driver tạo SQL array tạm; giải phóng ngay tại biên JDBC, không giữ connection/array ở principal. */
    static Set<String> strings(ResultSet row, String column) throws SQLException {
        var array = row.getArray(column);
        try {
            return Set.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
    }
}
