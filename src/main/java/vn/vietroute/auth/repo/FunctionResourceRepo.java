package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.response.FunctionDTO;
import vn.vietroute.auth.entity.FunctionResource;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng FunctionResource; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class FunctionResourceRepo extends JpaRepositorySupport<FunctionResource, Long> {

    public FunctionResourceRepo(EntityManager manager, JdbcTransactions transactions) {
        super(FunctionResource.class, manager, transactions);
    }

    public Set<String> targets(Connection connection, String code) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT target FROM auth_function_resources WHERE function_code=? ORDER BY target"
            )
        ) {
            query.setString(1, code);
            try (ResultSet rows = query.executeQuery()) {
                Set<String> result = new LinkedHashSet<>();
                while (rows.next()) result.add(rows.getString(1));
                return Set.copyOf(result);
            }
        }
    }

    /** Một query cho binding toàn trang, không gọi targets trong vòng lặp từng Function. */
    public Map<String, Set<String>> targetsFor(Connection connection, List<String> functions) throws SQLException {
        Array codes = connection.createArrayOf("text", functions.toArray(String[]::new));
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT function_code,target FROM auth_function_resources WHERE function_code=ANY(?) ORDER BY function_code,target"
            )
        ) {
            query.setArray(1, codes);
            try (ResultSet rows = query.executeQuery()) {
                Map<String, Set<String>> result = new LinkedHashMap<>();
                while (rows.next())
                    result.computeIfAbsent(rows.getString(1), ignored -> new LinkedHashSet<>()).add(rows.getString(2));
                return result;
            }
        } finally {
            codes.free();
        }
    }

    /** Caller giữ khóa Function; xóa phần bỏ và batch upsert phần chọn, giữ nhãn menu đã cấu hình. */
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
                INSERT INTO auth_function_resources(function_code,kind,target,http_method,path_template,handler,description,name_vi,name_en,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(function_code,kind,target) DO UPDATE
                SET http_method=EXCLUDED.http_method,path_template=EXCLUDED.path_template,handler=EXCLUDED.handler,
                    name_vi=CASE WHEN auth_function_resources.name_vi=auth_function_resources.handler THEN EXCLUDED.name_vi ELSE auth_function_resources.name_vi END,
                    name_en=CASE WHEN auth_function_resources.name_en=auth_function_resources.handler THEN EXCLUDED.name_en ELSE auth_function_resources.name_en END,
                    description=EXCLUDED.description,updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP,version=auth_function_resources.version+1
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
