package vn.vietroute.auth.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.entity.RoleFunction;

/**
 * Repository bảng RoleFunction; CRUD JPA và SQL batch/query phức tạp cùng transaction server.
 */
public interface RoleFunctionRepo extends JpaRepository<RoleFunction, Long> {
    @Transactional(readOnly = true)
    @Query(
        value = "SELECT function_code FROM {h-schema}auth_role_functions WHERE role_code = :role ORDER BY function_code ",
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

    @Transactional(readOnly = true)
    @Query(
        value = "SELECT EXISTS ( SELECT 1 FROM {h-schema}auth_role_functions WHERE function_code = :code ) ",
        nativeQuery = true
    )
    boolean usesFunction(@Param("code") String code);

    /**
     * Role inactive vẫn giữ trần ủy quyền; không thêm điều kiện active làm mất bảo vệ này.
     */
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

    @Query(
        value = "SELECT DISTINCT target FROM {h-schema}auth_function_resources WHERE function_code IN (:codes) ",
        nativeQuery = true
    )
    Set<String> assignedTargets(@Param("codes") Set<String> codes);

    /**
     * Kiểm cả tập trước batch mapping, giữ nguyên điều kiện tồn tại của function.
     */
    @Query(
        value = "SELECT COUNT(*) FROM {h-schema}auth_functions WHERE code IN (:codes) AND NOT deleted ",
        nativeQuery = true
    )
    long availableFunctions(@Param("codes") Set<String> codes);

    @Transactional(readOnly = true)
    default Set<String> functionTargets(Set<String> functions) {
        return functions.isEmpty() ? Set.of() : assignedTargets(functions);
    }
}
