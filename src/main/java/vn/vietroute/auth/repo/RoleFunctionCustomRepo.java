package vn.vietroute.auth.repo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ. */
@Repository
public class RoleFunctionCustomRepo {

    /** Hai statement theo tập; kiểm mã tồn tại kể cả khi tập rỗng, FK và transaction bảo vệ cạnh tranh. */
    public void replace(Connection connection, String role, Set<String> functions, long actor) throws SQLException {
        Array codes = connection.createArrayOf("text", functions.toArray(String[]::new));
        try {
            try (
                PreparedStatement query = connection.prepareStatement(
                    "DELETE FROM auth_role_functions WHERE role_code=? AND NOT(function_code=ANY(?))"
                )
            ) {
                query.setString(1, role);
                query.setArray(2, codes);
                query.executeUpdate();
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    INSERT INTO auth_role_functions(role_code, function_code, created_by, updated_by)
                    SELECT ?, code, ?, ?
                    FROM UNNEST(?::TEXT []) code
                    ON CONFLICT DO NOTHING
                    """
                )
            ) {
                query.setString(1, role);
                query.setLong(2, actor);
                query.setLong(3, actor);
                query.setArray(4, codes);
                query.executeUpdate();
            }
        } finally {
            codes.free();
        }
    }

    /** Endpoint function đổi thì snapshot gán role cũ cũng hết hạn; một UPDATE cho toàn bộ role liên quan. */
    public void touchRoles(Connection connection, String function, long actor) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE auth_roles r
                SET version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE EXISTS (
                    SELECT 1
                    FROM auth_role_functions rf
                    WHERE rf.role_code = r.code AND rf.function_code = ?
                )
                """
            )
        ) {
            query.setLong(1, actor);
            query.setString(2, function);
            query.executeUpdate();
        }
    }
}
