package vn.vietroute.auth.repo.custom;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.FunctionDTO;

/**
 * Ghi batch metadata role/function/resource, cùng transaction startup/sync.
 */
@Repository
public class RoleCustomRepo {

    public void initialize(Connection connection, JsonNode data) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_roles(code, name_vi, name_en, all_scopes, protected_role)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (JsonNode role : data.path("roles")) {
                query.setString(1, role.path("code").asText());
                query.setString(2, role.path("nameVi").asText());
                query.setString(3, role.path("nameEn").asText());
                query.setBoolean(4, role.path("allScopes").asBoolean());
                query.setBoolean(5, role.path("protectedRole").asBoolean());
                query.addBatch();
            }
            query.executeBatch();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_functions(code, name_vi, name_en, icon, sort_order)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (JsonNode function : data.path("functions")) {
                query.setString(1, function.path("code").asText());
                query.setString(2, function.path("nameVi").asText());
                query.setString(3, function.path("nameEn").asText());
                query.setString(4, function.path("icon").asText("shield"));
                query.setInt(5, function.path("sortOrder").asInt());
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    public void synchronizeEndpoints(Connection connection, JsonNode data, List<FunctionDTO> endpoints)
        throws SQLException {
        initialize(connection, data);
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_function_resources(function_code, kind, target, http_method, path_template,
                    handler, description, name_vi, name_en, icon, sort_order)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE EXISTS (
                    SELECT 1
                    FROM auth_functions
                    WHERE code = ? AND NOT resources_initialized AND NOT deleted
                )
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (JsonNode function : data.path("functions"))
                for (JsonNode item : function.path("resources")) {
                    FunctionDTO endpoint = endpoints
                        .stream()
                        .filter(value -> value.getTarget().equals(item.path("target").asText()))
                        .findFirst()
                        .orElse(null);
                    if (endpoint == null || endpoint.isPublicEndpoint() || endpoint.isSelfService()) continue;
                    insert.setString(1, function.path("code").asText());
                    insert.setString(2, endpoint.getKind());
                    insert.setString(3, endpoint.getTarget());
                    insert.setString(4, endpoint.getHttpMethod());
                    insert.setString(5, endpoint.getPathTemplate());
                    insert.setString(6, endpoint.getHandler());
                    insert.setString(7, endpoint.getDescription());
                    insert.setString(8, item.path("nameVi").asText(endpoint.getDescription()));
                    insert.setString(9, item.path("nameEn").asText(endpoint.getDescription()));
                    insert.setString(10, item.path("icon").asText("shield"));
                    insert.setInt(11, item.path("sortOrder").asInt());
                    insert.setString(12, function.path("code").asText());
                    insert.addBatch();
                }
            insert.executeBatch();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_role_functions(role_code, function_code)
                SELECT ?, ?
                WHERE EXISTS (
                    SELECT 1
                    FROM auth_roles
                    WHERE code = ? AND NOT functions_initialized AND NOT deleted
                )
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (JsonNode role : data.path("roles"))
                for (JsonNode function : role.path("functions")) {
                    insert.setString(1, role.path("code").asText());
                    insert.setString(2, function.asText());
                    insert.setString(3, role.path("code").asText());
                    insert.addBatch();
                }
            insert.executeBatch();
        }
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_functions SET resources_initialized=TRUE WHERE NOT resources_initialized"
            )
        ) {
            update.executeUpdate();
        }
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_roles SET functions_initialized=TRUE WHERE NOT functions_initialized"
            )
        ) {
            update.executeUpdate();
        }
    }
}
