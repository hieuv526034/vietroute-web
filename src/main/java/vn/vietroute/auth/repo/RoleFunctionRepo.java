package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.RoleFunction;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng RoleFunction; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class RoleFunctionRepo extends JpaRepositorySupport<RoleFunction, Long> {

    public RoleFunctionRepo(EntityManager manager, JdbcTransactions transactions) {
        super(RoleFunction.class, manager, transactions);
    }

    public Set<String> functions(Connection connection, String role) throws SQLException {
        return strings(connection, "SELECT function_code FROM auth_role_functions WHERE role_code=?", role);
    }

    public Set<String> targets(Connection connection, String role) throws SQLException {
        return strings(
            connection,
            """
            SELECT DISTINCT fr.target
            FROM auth_role_functions rf
            JOIN auth_functions f ON f.code = rf.function_code AND NOT f.deleted
            JOIN auth_function_resources fr ON fr.function_code = f.code
            WHERE rf.role_code = ?
            """,
            role
        );
    }

    private Set<String> strings(Connection connection, String sql, String role) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, role);
            try (ResultSet rows = query.executeQuery()) {
                Set<String> result = new LinkedHashSet<>();
                while (rows.next()) result.add(rows.getString(1));
                return Set.copyOf(result);
            }
        }
    }

    /** Hai statement theo tập; kiểm mã tồn tại kể cả khi tập rỗng, FK và transaction bảo vệ cạnh tranh. */
    public void replace(Connection connection, String role, Set<String> functions, long actor) throws SQLException {
        Array codes = connection.createArrayOf("text", functions.toArray(String[]::new));
        try {
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM auth_functions WHERE code=ANY(?) AND NOT deleted"
                )
            ) {
                query.setArray(1, codes);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    if (row.getLong(1) != functions.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
            }
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
                    FROM unnest(?::text []) code
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

    public boolean usesFunction(Connection connection, String code) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM auth_role_functions WHERE function_code=? LIMIT 1"
            )
        ) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
                return row.next();
            }
        }
    }

    /** Kể cả role đang tắt vẫn giữ trần dữ liệu, tránh nới quyền gián tiếp khi bật lại. */
    public boolean hasAllScopesRole(Connection connection, String code) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT 1
                FROM auth_role_functions rf
                JOIN auth_roles r ON r.code = rf.role_code
                WHERE rf.function_code = ? AND r.all_scopes
                LIMIT 1
                """
            )
        ) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
                return row.next();
            }
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

    public Set<String> functionTargets(Connection connection, Set<String> functions) throws SQLException {
        Array codes = connection.createArrayOf("text", functions.toArray(String[]::new));
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT DISTINCT target FROM auth_function_resources WHERE function_code=ANY(?)"
            )
        ) {
            query.setArray(1, codes);
            try (ResultSet rows = query.executeQuery()) {
                Set<String> result = new LinkedHashSet<>();
                while (rows.next()) result.add(rows.getString(1));
                return Set.copyOf(result);
            }
        } finally {
            codes.free();
        }
    }
}
