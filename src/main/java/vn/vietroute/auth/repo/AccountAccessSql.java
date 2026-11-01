package vn.vietroute.auth.repo;

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
    , ARRAY(SELECT r.code FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
        WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active ORDER BY r.code) AS role_codes
    , ARRAY(SELECT DISTINCT op.code FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
        JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
        CROSS JOIN LATERAL unnest(rf.operation_codes) op(code) JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted
        WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active ORDER BY op.code) AS permission_codes
    """;
    static final String ARRAYS =
        AUTHORITIES +
        """
        , ARRAY(SELECT DISTINCT fr.target FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
            JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
            JOIN auth_function_resources fr ON fr.function_code=f.code WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active AND NOT fr.public_route ORDER BY fr.target) AS resource_targets
        , ARRAY[]::text[] AS legacy_permission_codes
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
