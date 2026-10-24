package vn.vietroute.auth.repository;

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
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.dto.response.RolePermissionsResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
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
                    "SELECT code FROM auth_roles WHERE active AND NOT deleted ORDER BY code LIMIT 512"
                );
                ResultSet rows = query.executeQuery()
            ) {
                while (rows.next()) {
                    roles.add(Role.valueOf(rows.getString(1)));
                }
            }
            return List.copyOf(roles);
        });
    }

    public List<PermissionResponse> permissions(Locale locale) {
        return read(connection -> {
            List<PermissionResponse> permissions = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT code,name_vi,name_en FROM auth_permissions WHERE active ORDER BY code LIMIT 512"
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
            return List.copyOf(permissions);
        });
    }

    /** Một statement cho danh sách role/mapping/số user: tránh mở connection và query N+1 theo từng role. */
    public List<RolePermissionsResponse> roleSummaries() {
        return read(connection -> {
            List<RolePermissionsResponse> result = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT r.code,r.permission_version,r.updated_at,r.updated_by,r.active,(SELECT COUNT(*) FROM auth_user_roles ur WHERE ur.role_code=r.code),COALESCE((SELECT string_agg(rp.permission_code,',' ORDER BY rp.permission_code) FROM auth_role_permissions rp WHERE rp.role_code=r.code),'') FROM auth_roles r WHERE NOT r.deleted ORDER BY r.code LIMIT 512"
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
        for (Role role : roles) {
            requireActive(connection, "SELECT 1 FROM auth_roles WHERE code=? AND active AND NOT deleted", role.name());
        }
        for (Permission permission : permissions) {
            requireActive(connection, "SELECT 1 FROM auth_permissions WHERE code=? AND active", permission.name());
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
                "SELECT permission_version, updated_at, updated_by, (SELECT COUNT(*) FROM auth_user_roles ur WHERE ur.role_code=auth_roles.code), active FROM auth_roles WHERE code=? AND NOT deleted"
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
                        "SELECT permission_code FROM auth_role_permissions WHERE role_code=? ORDER BY permission_code"
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
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_roles SET permission_version=permission_version+1, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE code=? AND permission_version=?"
            )
        ) {
            update.setLong(1, actor);
            update.setString(2, role.name());
            update.setLong(3, version);
            if (update.executeUpdate() != 1) {
                throw new BusinessException(ErrorCode.STALE_VERSION);
            }
        }
        try (
            PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM auth_role_permissions WHERE role_code=?"
            )
        ) {
            delete.setString(1, role.name());
            delete.executeUpdate();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by) VALUES (?,?,?,?)"
            )
        ) {
            for (Permission permission : permissions) {
                insert.setString(1, role.name());
                insert.setString(2, permission.name());
                insert.setLong(3, actor);
                insert.setLong(4, actor);
                insert.addBatch();
            }
            insert.executeBatch();
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
                "SELECT role_code FROM auth_user_roles WHERE user_id=? ORDER BY role_code"
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
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_users SET version=version+1, token_version=token_version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?"
            )
        ) {
            update.setLong(1, actor);
            update.setLong(2, id);
            update.setLong(3, version);
            if (update.executeUpdate() != 1) {
                throw new BusinessException(ErrorCode.STALE_VERSION);
            }
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM auth_user_roles WHERE user_id=?")) {
            delete.setLong(1, id);
            delete.executeUpdate();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) VALUES (?,?,?,?)"
            )
        ) {
            for (Role role : roles) {
                insert.setLong(1, id);
                insert.setString(2, role.name());
                insert.setLong(3, actor);
                insert.setLong(4, actor);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
