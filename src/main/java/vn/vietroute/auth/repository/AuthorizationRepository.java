package vn.vietroute.auth.repository;

import java.util.LinkedHashSet;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Đọc trạng thái và mapping hiện hành trong một statement/snapshot PostgreSQL, không tải credential. */
@Repository
public class AuthorizationRepository extends BaseRepository {

    public AuthorizationRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** LEFT JOIN giữ user không có quyền; role tắt/xóa và permission tắt không tham gia tập hiệu lực. */
    public AuthorizationState find(long id) {
        return read(connection -> {
            try (
                var query = connection.prepareStatement(
                    """
                    SELECT u.id, u.active, u.activation_pending, u.must_change_password, u.token_version,
                           r.code AS role_code, p.code AS permission_code
                    FROM auth_users u
                    LEFT JOIN auth_user_roles ur ON ur.user_id = u.id
                    LEFT JOIN auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
                    LEFT JOIN auth_role_permissions rp ON rp.role_code = r.code
                    LEFT JOIN auth_permissions p ON p.code = rp.permission_code AND p.active
                    WHERE u.id = ?
                    """
                )
            ) {
                query.setLong(1, id);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) return null;
                    boolean active = rows.getBoolean("active");
                    boolean pending = rows.getBoolean("activation_pending");
                    boolean temporary = rows.getBoolean("must_change_password");
                    long version = rows.getLong("token_version");
                    var roles = new LinkedHashSet<Role>();
                    var permissions = new LinkedHashSet<String>();
                    do {
                        String role = rows.getString("role_code");
                        String permission = rows.getString("permission_code");
                        if (role != null) roles.add(Role.valueOf(role));
                        if (permission != null) permissions.add(permission);
                    } while (rows.next());
                    return new AuthorizationState(id, active, pending, temporary, version, roles, permissions);
                }
            }
        });
    }
}
