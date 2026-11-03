package vn.vietroute.auth.repo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.entity.FunctionResource;

/** Repository bảng FunctionResource; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
public interface FunctionResourceRepo extends JpaRepository<FunctionResource, Long> {
    @Transactional(readOnly = true)
    @Query(
        value = """
        SELECT target
        FROM {h-schema}auth_function_resources
        WHERE function_code = :code
        ORDER BY target
        """,
        nativeQuery = true
    )
    Set<String> targets(@Param("code") String code);

    @Transactional(readOnly = true)
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

    /** Đọc tập cho toàn trang; vòng lặp chỉ map dữ liệu đã tải, không query từng function. */
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

    /** Caller giữ khóa Function; xóa phần bỏ và batch upsert phần chọn, giữ nhãn menu đã cấu hình. */
    default void replace(Connection connection, String code, List<FunctionDTO> selected, long actor)
        throws SQLException {
        Array targets = connection.createArrayOf(
            "text",
            selected
                .stream()
                .map(FunctionDTO::getTarget)
                .toArray(String[]::new)
        );
        try (
            PreparedStatement query = connection.prepareStatement(
                "DELETE FROM auth_function_resources WHERE function_code=? AND NOT(target=ANY(?))"
            )
        ) {
            query.setString(1, code);
            query.setArray(2, targets);
            query.executeUpdate();
        } finally {
            targets.free();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_function_resources(function_code, kind, target, http_method, path_template,
                    handler, description, name_vi, name_en, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (function_code, kind, target)
                DO UPDATE
                SET http_method = EXCLUDED.http_method,
                    path_template = EXCLUDED.path_template,
                    handler = EXCLUDED.handler,
                    name_vi = CASE
                        WHEN auth_function_resources.name_vi = auth_function_resources.handler
                            THEN EXCLUDED.name_vi
                        ELSE auth_function_resources.name_vi
                    END,
                    name_en = CASE
                        WHEN auth_function_resources.name_en = auth_function_resources.handler
                            THEN EXCLUDED.name_en
                        ELSE auth_function_resources.name_en
                    END,
                    description = EXCLUDED.description,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = CURRENT_TIMESTAMP,
                    version = auth_function_resources.version + 1
                """
            )
        ) {
            for (FunctionDTO item : selected) {
                query.setString(1, code);
                query.setString(2, item.getKind());
                query.setString(3, item.getTarget());
                query.setString(4, item.getHttpMethod());
                query.setString(5, item.getPathTemplate());
                query.setString(6, item.getHandler());
                query.setString(7, item.getDescription());
                query.setString(8, item.getDescription());
                query.setString(9, item.getDescription());
                query.setLong(10, actor);
                query.setLong(11, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }
}
