package vn.vietroute.auth.repo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng Role; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class RoleRepo extends JpaRepositorySupport<Role, String> {

    private final FunctionResourceRepo functionResources;

    public RoleRepo(EntityManager manager, JdbcTransactions transactions, FunctionResourceRepo resources) {
        super(Role.class, manager, transactions);
        this.functionResources = resources;
    }

    /** Query lọc/count/page nằm ở Repo; LIKE escape giữ nguyên %, _ và ! của người dùng. */
    public Page<Role> search(RoleSearch search) {
        String keyword =
            "%" +
            search.getQuery().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") +
            "%";
        Specification<Role> spec = (item, query, cb) ->
            cb.and(
                cb.isFalse(item.get("deleted")),
                search.getActive() == null ? cb.conjunction() : cb.equal(item.get("active"), search.getActive()),
                cb.or(
                    cb.like(cb.lower(item.get("code")), keyword, '!'),
                    cb.like(cb.lower(item.get("nameVi")), keyword, '!'),
                    cb.like(cb.lower(item.get("nameEn")), keyword, '!')
                )
            );
        return findAll(spec, PageRequest.of(search.getPage(), search.getSize(), Sort.by("code")));
    }

    public List<Role> all() {
        return entityManager
            .createQuery("select r from Role r where r.deleted=false order by r.code", Role.class)
            .getResultList();
    }

    public Role lock(String code) {
        return entityManager.find(Role.class, code, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Root startup tạo metadata trước; danh bạ endpoint thật chỉ có sau servlet refresh. */
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

    /** Chỉ startup/sync tường minh; không tự khôi phục grant ordinary role hoặc thêm endpoint vào function đã init. */
    public void synchronizeEndpoints(Connection connection, JsonNode data, List<FunctionDTO> endpoints)
        throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
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
                "UPDATE auth_functions SET resources_initialized=true WHERE NOT resources_initialized"
            )
        ) {
            update.executeUpdate();
        }
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_roles SET functions_initialized=true WHERE NOT functions_initialized"
            )
        ) {
            update.executeUpdate();
        }
        repairBootstrap(connection, data, endpoints, 0);
    }

    /** Bootstrap nhận tập endpoint hiện tại khi init/sync/recovery được bật; runtime vẫn cần mapping DB thật. */
    public void repairBootstrap(Connection connection, JsonNode data, List<FunctionDTO> endpoints, long actor)
        throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE auth_roles
                SET active = true, deleted = false, all_scopes = true, protected_role = true, updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE code = ?
                    AND (NOT active OR deleted OR NOT all_scopes OR NOT protected_role)
                """
            )
        ) {
            update.setLong(1, actor);
            update.setString(2, data.path("bootstrapRole").asText());
            update.executeUpdate();
        }
        functionResources.replace(
            connection,
            data.path("bootstrapFunction").asText("ADMIN_CONSOLE"),
            endpoints
                .stream()
                .filter(item -> !item.isPublicEndpoint() && !item.isSelfService())
                .toList(),
            actor
        );
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE auth_functions
                SET active = true, deleted = false, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE code = ?
                    AND (NOT active OR deleted)
                """
            )
        ) {
            update.setLong(1, actor);
            update.setString(2, data.path("bootstrapFunction").asText("ADMIN_CONSOLE"));
            update.executeUpdate();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_role_functions(role_code, function_code, created_by, updated_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            insert.setString(1, data.path("bootstrapRole").asText());
            insert.setString(2, data.path("bootstrapFunction").asText("ADMIN_CONSOLE"));
            insert.setLong(3, actor);
            insert.setLong(4, actor);
            insert.executeUpdate();
        }
    }
}
