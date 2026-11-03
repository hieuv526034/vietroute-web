package vn.vietroute.auth.repo.custom;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.FunctionDTO;

/**
 * SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ.
 */
@Repository
public class FunctionResourceCustomRepo {

    /**
     * Caller giữ khóa Function; xóa phần bỏ và batch upsert phần chọn, giữ nhãn menu đã cấu hình.
     */
    public void replace(Connection connection, String code, List<FunctionDTO> selected, long actor)
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
