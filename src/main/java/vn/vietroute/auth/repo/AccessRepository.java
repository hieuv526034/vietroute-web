package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.response.registry.PermissionResponse;
import vn.vietroute.auth.dto.response.role.RolePermissionsResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.constant.AccessConstants.Limits;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** DAO quyền chức năng; mọi thay đổi dùng connection transaction do service sở hữu. */
@Repository
public class AccessRepository extends BaseRepository {

    public AccessRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    public List<Role> roles() {
        return read(connection -> {
            List<Role> roles = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT code FROM auth_roles WHERE active AND NOT deleted ORDER BY code LIMIT " +
                        (Limits.REGISTRY_MAX + 1)
                );
                ResultSet rows = query.executeQuery()
            ) {
                while (rows.next()) {
                    roles.add(Role.valueOf(rows.getString(1)));
                }
            }
            if (roles.size() > Limits.REGISTRY_MAX) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                Limits.REGISTRY_MAX
            );
            return List.copyOf(roles);
        });
    }

    public List<PermissionResponse> permissions(Locale locale) {
        return read(connection -> {
            List<PermissionResponse> permissions = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT code,name_vi,name_en FROM auth_functions WHERE active AND NOT deleted AND code=ANY(operation_codes) ORDER BY code LIMIT " +
                        (Limits.REGISTRY_MAX + 1)
                );
                ResultSet rows = query.executeQuery()
            ) {
                while (rows.next()) {
                    Permission permission = Permission.valueOf(rows.getString(1));
                    permissions.add(
                        new PermissionResponse(
                            permission,
                            permission.resource(),
                            permission.actions(),
                            permission.messageKey(),
                            rows.getString("en".equals(locale.getLanguage()) ? 3 : 2)
                        )
                    );
                }
            }
            if (permissions.size() > Limits.REGISTRY_MAX) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                Limits.REGISTRY_MAX
            );
            return List.copyOf(permissions);
        });
    }

    /** Một statement cho danh sách role/mapping/số user: tránh mở connection và query N+1 theo từng role. */
    public List<RolePermissionsResponse> roleSummaries() {
        return read(connection -> {
            List<RolePermissionsResponse> result = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT r.code,r.permission_version,r.updated_at,r.updated_by,r.active,(SELECT COUNT(*) FROM auth_user_roles ur WHERE ur.role_code=r.code AND ur.scope_id=0),COALESCE((SELECT string_agg(rp.permission_code,',' ORDER BY rp.permission_code) FROM (SELECT DISTINCT rf.role_code,op.code AS permission_code FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted CROSS JOIN LATERAL unnest(rf.operation_codes) op(code) JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted) rp WHERE rp.role_code=r.code),'') FROM auth_roles r WHERE NOT r.deleted ORDER BY r.code LIMIT " +
                        (Limits.REGISTRY_MAX + 1)
                );
                var rows = query.executeQuery()
            ) {
                while (rows.next()) {
                    String encoded = rows.getString(7);
                    Set<Permission> permissions = encoded.isEmpty()
                        ? Set.of()
                        : Arrays.stream(encoded.split(",")).map(Permission::valueOf).collect(Collectors.toSet());
                    result.add(
                        new RolePermissionsResponse(
                            Role.valueOf(rows.getString(1)),
                            Set.copyOf(permissions),
                            rows.getLong(2),
                            rows.getTimestamp(3).toInstant(),
                            rows.getObject(4, Long.class),
                            rows.getLong(6),
                            rows.getBoolean(5)
                        )
                    );
                }
            }
            if (result.size() > Limits.REGISTRY_MAX) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                Limits.REGISTRY_MAX
            );
            return List.copyOf(result);
        });
    }

    /** Cho sửa mapping khi role đang tắt để quản trị chuẩn bị quyền trước khi bật; không cho sửa tombstone. */
    public void requireExistingRole(Connection connection, Role role) throws SQLException {
        requireActive(connection, "SELECT 1 FROM auth_roles WHERE code=? AND NOT deleted", role.name());
    }

    /** Kiểm tập mapping tồn tại/đang bật trong cùng transaction, chặn mã tự chế hoặc role đã ngừng. */
    public void validateMappings(Connection connection, Set<Role> roles, Set<Permission> permissions)
        throws SQLException {
        if (roles.isEmpty() && permissions.isEmpty()) return;
        var roleCodes = connection.createArrayOf(
            "text",
            roles
                .stream()
                .map(Role::name)
                .toArray(String[]::new)
        );
        var permissionCodes = connection.createArrayOf(
            "text",
            permissions
                .stream()
                .map(Permission::name)
                .toArray(String[]::new)
        );
        // Một statement cho toàn tập; không đổi điều kiện active/tombstone hoặc tin mã client chưa có trong registry.
        try (
            var query = connection.prepareStatement(
                """
                SELECT NOT EXISTS(SELECT code FROM unnest(?::text[]) wanted(code)
                    WHERE NOT EXISTS(SELECT 1 FROM auth_roles r WHERE r.code=wanted.code AND r.active AND NOT r.deleted))
                  AND NOT EXISTS(SELECT code FROM unnest(?::text[]) wanted(code)
                    WHERE NOT EXISTS(SELECT 1 FROM auth_functions p WHERE p.code=wanted.code AND p.active AND NOT p.deleted AND p.code=ANY(p.operation_codes)))
                """
            )
        ) {
            query.setArray(1, roleCodes);
            query.setArray(2, permissionCodes);
            try (var row = query.executeQuery()) {
                row.next();
                if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } finally {
            roleCodes.free();
            permissionCodes.free();
        }
    }

    private void requireActive(Connection connection, String sql, String code) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, code);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
            }
        }
    }

    public RolePermissionsResponse role(Role role) {
        return read(connection -> role(connection, role));
    }

    public RolePermissionsResponse role(Connection connection, Role role) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT permission_version, updated_at, updated_by, (SELECT COUNT(*) FROM auth_user_roles ur WHERE ur.role_code=auth_roles.code AND ur.scope_id=0), active FROM auth_roles WHERE code=? AND NOT deleted"
            )
        ) {
            query.setString(1, role.name());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                Set<Permission> permissions = new LinkedHashSet<>();
                try (
                    PreparedStatement items = connection.prepareStatement(
                        "SELECT permission_code FROM (SELECT DISTINCT rf.role_code,op.code AS permission_code FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted CROSS JOIN LATERAL unnest(rf.operation_codes) op(code) JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted) WHERE role_code=? ORDER BY permission_code"
                    )
                ) {
                    items.setString(1, role.name());
                    try (ResultSet result = items.executeQuery()) {
                        while (result.next()) {
                            permissions.add(Permission.valueOf(result.getString(1)));
                        }
                    }
                }
                return new RolePermissionsResponse(
                    role,
                    Set.copyOf(permissions),
                    row.getLong(1),
                    row.getTimestamp(2).toInstant(),
                    row.getObject(3, Long.class),
                    row.getLong(4),
                    row.getBoolean(5)
                );
            }
        }
    }

    /** Optimistic UPDATE lấy khóa role trước khi xóa/chèn mapping, tránh hai người sửa lẫn tập quyền. */
    public void replacePermissions(
        Connection connection,
        Role role,
        Set<Permission> permissions,
        long version,
        long actor
    ) throws SQLException {
        bump(connection, role, version, actor);
        try (var query = connection.prepareStatement("DELETE FROM auth_role_functions WHERE role_code=?")) {
            query.setString(1, role.name());
            query.executeUpdate();
        }
        var codes = connection.createArrayOf(
            "text",
            permissions
                .stream()
                .map(Permission::name)
                .toArray(String[]::new)
        );
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_by,updated_by) SELECT ?,code,operation_codes,?,? FROM auth_functions WHERE code=ANY(?) AND active AND NOT deleted"
            )
        ) {
            query.setString(1, role.name());
            query.setLong(2, actor);
            query.setLong(3, actor);
            query.setArray(4, codes);
            query.executeUpdate();
        } finally {
            codes.free();
        }
    }

    /** CAS mapping chung với API function; caller giữ advisory lock quản trị. */
    public static void bump(Connection connection, Role role, long version, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_roles SET permission_version=permission_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND permission_version=? AND NOT deleted"
            )
        ) {
            query.setLong(1, actor);
            query.setString(2, role.name());
            query.setLong(3, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Khóa transaction chung cho sửa role user, giữ invariant luôn có ít nhất một admin hoạt động. */
    public void lockRoleAssignments(Connection connection) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
    }

    public boolean onlyActiveAdmin(Connection connection, long userId) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT count(*) FROM auth_users u JOIN auth_user_roles r ON r.user_id=u.id WHERE u.active AND r.role_code='ADMIN' AND u.id<>?"
            )
        ) {
            query.setLong(1, userId);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getLong(1) == 0;
            }
        }
    }

    /** Snapshot mapping thô cho audit, gồm role đang tắt để việc gỡ không bị mất dấu trong lịch sử. */
    public Set<Role> assignedRoles(Connection connection, long id) throws SQLException {
        Set<Role> result = new LinkedHashSet<>();
        try (
            var query = connection.prepareStatement(
                "SELECT role_code FROM auth_user_roles WHERE user_id=? AND scope_id=0 ORDER BY role_code"
            )
        ) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(Role.valueOf(rows.getString(1)));
            }
        }
        return Set.copyOf(result);
    }

    public void replaceRoles(Connection connection, long id, Set<Role> roles, long version, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET version=version+1,token_version=token_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND version=? AND NOT deleted"
            )
        ) {
            query.setLong(1, actor);
            query.setLong(2, id);
            query.setLong(3, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        var codes = connection.createArrayOf(
            "text",
            roles
                .stream()
                .map(Role::name)
                .toArray(String[]::new)
        );
        try {
            // Lưu mốc thu hồi trước khi FK cascade xóa scoped role cuối; gán lại không nhận version cũ.
            try (
                var query = connection.prepareStatement(
                    "UPDATE auth_users u SET scope_membership_versions=u.scope_membership_versions||v.versions FROM (SELECT jsonb_object_agg(scope_id::text,next_version) AS versions FROM (SELECT scope_id,GREATEST(max(version),COALESCE((SELECT (scope_membership_versions->>ur.scope_id::text)::bigint FROM auth_users WHERE id=?),0))+1 AS next_version FROM auth_user_roles ur WHERE user_id=? AND scope_id>0 GROUP BY scope_id HAVING bool_or(NOT(role_code=ANY(?)))) affected) v WHERE u.id=? AND v.versions IS NOT NULL"
                )
            ) {
                query.setLong(1, id);
                query.setLong(2, id);
                query.setArray(3, codes);
                query.setLong(4, id);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "UPDATE auth_user_roles ur SET version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE ur.user_id=? AND ur.scope_id>0 AND EXISTS(SELECT 1 FROM auth_user_roles affected WHERE affected.user_id=ur.user_id AND affected.scope_id=ur.scope_id AND NOT(affected.role_code=ANY(?)))"
                )
            ) {
                query.setLong(1, actor);
                query.setLong(2, id);
                query.setArray(3, codes);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "DELETE FROM auth_user_roles WHERE user_id=? AND scope_id=0 AND NOT(role_code=ANY(?))"
                )
            ) {
                query.setLong(1, id);
                query.setArray(2, codes);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) SELECT ?,code,?,? FROM unnest(?::text[]) code ON CONFLICT DO NOTHING"
                )
            ) {
                query.setLong(1, id);
                query.setLong(2, actor);
                query.setLong(3, actor);
                query.setArray(4, codes);
                query.executeUpdate();
            }
        } finally {
            codes.free();
        }
    }
}
