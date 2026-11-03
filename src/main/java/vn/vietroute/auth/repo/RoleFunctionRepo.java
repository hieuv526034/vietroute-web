package vn.vietroute.auth.repo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.entity.RoleFunction;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Repository bảng RoleFunction; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
public interface RoleFunctionRepo extends JpaRepository<RoleFunction, Long> {
    @Transactional(readOnly = true)
    @Query(
        value = """
        SELECT function_code
        FROM {h-schema}auth_role_functions
        WHERE role_code = :role
        ORDER BY function_code
        """,
        nativeQuery = true
    )
    Set<String> functions(@Param("role") String role);

    @Transactional(readOnly = true)
    @Query(
        value = """
        SELECT DISTINCT fr.target
        FROM {h-schema}auth_role_functions rf
        JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND NOT f.deleted
        JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
        WHERE rf.role_code = :role
        """,
        nativeQuery = true
    )
    Set<String> targets(@Param("role") String role);

    /** Hai statement theo tập; kiểm mã tồn tại kể cả khi tập rỗng, FK và transaction bảo vệ cạnh tranh. */
    default void replace(Connection connection, String role, Set<String> functions, long actor) throws SQLException {
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

    @Transactional(readOnly = true)
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM {h-schema}auth_role_functions
            WHERE function_code = :code
        )
        """,
        nativeQuery = true
    )
    boolean usesFunction(@Param("code") String code);

    /** Role inactive vẫn giữ trần ủy quyền; không thêm điều kiện active làm mất bảo vệ này. */
    @Transactional(readOnly = true)
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM {h-schema}auth_role_functions rf
            JOIN {h-schema}auth_roles r ON r.code = rf.role_code
            WHERE rf.function_code = :code AND r.all_scopes
        )
        """,
        nativeQuery = true
    )
    boolean hasAllScopesRole(@Param("code") String code);

    /** Endpoint function đổi thì snapshot gán role cũ cũng hết hạn; một UPDATE cho toàn bộ role liên quan. */
    default void touchRoles(Connection connection, String function, long actor) throws SQLException {
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

    @Query(
        value = """
        SELECT DISTINCT target
        FROM {h-schema}auth_function_resources
        WHERE function_code IN (:codes)
        """,
        nativeQuery = true
    )
    Set<String> assignedTargets(@Param("codes") Set<String> codes);

    @Transactional(readOnly = true)
    default Set<String> functionTargets(Set<String> functions) {
        return functions.isEmpty() ? Set.of() : assignedTargets(functions);
    }
}
