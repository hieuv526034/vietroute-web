package vn.vietroute.auth.repo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.entity.FunctionResource;

/**
 * Repository bảng FunctionResource; CRUD JPA và SQL batch/query phức tạp cùng transaction server.
 */
public interface FunctionResourceRepo extends JpaRepository<FunctionResource, Long> {
    @Transactional(readOnly = true)
    @Query(
        value = "SELECT target FROM {h-schema}auth_function_resources WHERE function_code = :code ORDER BY target ",
        nativeQuery = true
    )
    Set<String> targets(@Param("code") String code);

    @Query(
        value = """
        SELECT function_code, target
        FROM {h-schema}auth_function_resources
        WHERE function_code IN (:codes)
        ORDER BY function_code, target
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> targetsRows(@Param("codes") List<String> codes);

    /**
     * Đọc tập cho toàn trang; vòng lặp chỉ map dữ liệu đã tải, không query từng function.
     */
    @Transactional(readOnly = true)
    default Map<String, Set<String>> targetsFor(List<String> functions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        if (functions.isEmpty()) return result;
        for (Map<String, Object> row : targetsRows(functions)) {
            result
                .computeIfAbsent((String) row.get("function_code"), ignored -> new LinkedHashSet<>())
                .add((String) row.get("target"));
        }
        return result;
    }
}
