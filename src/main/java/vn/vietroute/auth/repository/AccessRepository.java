package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;
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

    public RolePermissionsResponse role(Role role) {
        return read(connection -> role(connection, role));
    }

    public RolePermissionsResponse role(Connection connection, Role role) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT permission_version, updated_at, updated_by FROM vietroute.auth_roles WHERE code=?"
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
                        "SELECT permission_code FROM vietroute.auth_role_permissions WHERE role_code=? ORDER BY permission_code"
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
                "UPDATE vietroute.auth_roles SET permission_version=permission_version+1, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE code=? AND permission_version=?"
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
                "DELETE FROM vietroute.auth_role_permissions WHERE role_code=?"
            )
        ) {
            delete.setString(1, role.name());
            delete.executeUpdate();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.auth_role_permissions(role_code,permission_code) VALUES (?,?)"
            )
        ) {
            for (Permission permission : permissions) {
                insert.setString(1, role.name());
                insert.setString(2, permission.name());
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
                "SELECT count(*) FROM vietroute.auth_users u JOIN vietroute.auth_user_roles r ON r.user_id=u.id WHERE u.active AND r.role_code='ADMIN' AND u.id<>?"
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
                "UPDATE vietroute.auth_users SET version=version+1, token_version=token_version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?"
            )
        ) {
            update.setLong(1, actor);
            update.setLong(2, id);
            update.setLong(3, version);
            if (update.executeUpdate() != 1) {
                throw new BusinessException(ErrorCode.STALE_VERSION);
            }
        }
        try (
            PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM vietroute.auth_user_roles WHERE user_id=?"
            )
        ) {
            delete.setLong(1, id);
            delete.executeUpdate();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.auth_user_roles(user_id,role_code) VALUES (?,?)"
            )
        ) {
            for (Role role : roles) {
                insert.setLong(1, id);
                insert.setString(2, role.name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
