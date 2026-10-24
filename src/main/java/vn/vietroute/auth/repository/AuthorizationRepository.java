package vn.vietroute.auth.repository;

import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Đọc trạng thái và mapping hiện hành trong một statement/snapshot PostgreSQL, không tải credential. */
@Repository
public class AuthorizationRepository extends BaseRepository {

    public AuthorizationRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Mảng rỗng giữ user không quyền; role tắt/xóa và permission tắt bị loại trong cùng snapshot SQL. */
    public AuthorizationState find(long id) {
        return read(connection -> {
            try (
                var query = connection.prepareStatement(
                    "SELECT u.id,u.active,u.activation_pending,u.must_change_password,u.token_version" +
                        AccountAccessSql.ARRAYS +
                        " FROM auth_users u WHERE u.id=?"
                )
            ) {
                query.setLong(1, id);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) return null;
                    boolean active = rows.getBoolean("active");
                    boolean pending = rows.getBoolean("activation_pending");
                    boolean temporary = rows.getBoolean("must_change_password");
                    long version = rows.getLong("token_version");
                    return new AuthorizationState(
                        id,
                        active,
                        pending,
                        temporary,
                        version,
                        AccountAccessSql.roles(rows),
                        AccountAccessSql.strings(rows, "permission_codes")
                    );
                }
            }
        });
    }
}
