package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
                    "SELECT code FROM auth_roles ORDER BY code LIMIT 512"
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

    /** Kiểm tập mapping tồn tại/đang bật trong cùng transaction, chặn mã tự chế hoặc role đã ngừng. */
    public void validateMappings(Connection connection, Set<Role> roles, Set<Permission> permissions)
        throws SQLException {
        for (Role role : roles) {
            requireActive(connection, "SELECT 1 FROM auth_roles WHERE code=? AND active", role.name());
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
                "SELECT permission_version, updated_at, updated_by FROM auth_roles WHERE code=?"
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
                    row.getObject(3, Long.class)
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
