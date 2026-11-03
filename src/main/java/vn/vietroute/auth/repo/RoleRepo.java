package vn.vietroute.auth.repo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.JpaRepositorySupport;

/** Repository bảng Role; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class RoleRepo extends JpaRepositorySupport<Role, String> {

    private final FunctionResourceCustomRepo functionResources;
    private final RoleCustomRepo batch;

    public RoleRepo(EntityManager manager, FunctionResourceCustomRepo resources, RoleCustomRepo batch) {
        super(Role.class, manager);
        this.functionResources = resources;
        this.batch = batch;
    }

    /** Query lọc/count/page nằm ở Repo; LIKE escape giữ nguyên %, _ và ! của người dùng. */
    public Page<Role> search(RoleSearch search) {
        String keyword =
            "%" +
            search.getQuery().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") +
            "%";
        Query count = entityManager.createNativeQuery(
            """
            SELECT COUNT(*)
            FROM {h-schema}auth_roles r
            WHERE NOT r.deleted
                AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
                AND (LOWER(r.code) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_vi) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_en) LIKE :keyword ESCAPE '!')
            """
        );
        count.setParameter("active", search.getActive());
        count.setParameter("keyword", keyword);
        long total = ((Number) count.getSingleResult()).longValue();
        Query page = entityManager.createNativeQuery(
            """
            SELECT r.*
            FROM {h-schema}auth_roles r
            WHERE NOT r.deleted
                AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
                AND (LOWER(r.code) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_vi) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_en) LIKE :keyword ESCAPE '!')
            ORDER BY r.code
            """,
            Role.class
        );
        page.setParameter("active", search.getActive());
        page.setParameter("keyword", keyword);
        page.setFirstResult(Math.toIntExact(search.offset()));
        page.setMaxResults(search.getSize());
        List<?> values = page.getResultList();
        return new PageImpl<>(
            values.stream().map(Role.class::cast).toList(),
            PageRequest.of(search.getPage(), search.getSize()),
            total
        );
    }

    public List<Role> all() {
        return entityManager
            .createNativeQuery("SELECT * FROM {h-schema}auth_roles WHERE deleted = FALSE ORDER BY code", Role.class)
            .getResultList();
    }

    public Role lock(String code) {
        return entityManager.find(Role.class, code, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Root startup tạo metadata trước; danh bạ endpoint thật chỉ có sau servlet refresh. */

    /** Chỉ startup/sync tường minh; không tự khôi phục grant ordinary role hoặc thêm endpoint vào function đã init. */
    public void synchronizeEndpoints(Connection connection, JsonNode data, List<FunctionDTO> endpoints)
        throws SQLException {
        {
            Query lock = entityManager.createNativeQuery("SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT)");

            lock.getSingleResult();
        }
        batch.synchronizeEndpoints(connection, data, endpoints);
        repairBootstrap(connection, data, endpoints, 0);
    }

    /** Bootstrap nhận tập endpoint hiện tại khi init/sync/recovery được bật; runtime vẫn cần mapping DB thật. */
    public void repairBootstrap(Connection connection, JsonNode data, List<FunctionDTO> endpoints, long actor)
        throws SQLException {
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_roles
                SET active = TRUE, deleted = FALSE, all_scopes = TRUE, protected_role = TRUE, updated_by = :actor,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE code = :value2
                    AND (NOT active OR deleted OR NOT all_scopes OR NOT protected_role)
                """
            );

            update.setParameter("actor", actor);
            update.setParameter("value2", data.path("bootstrapRole").asText());
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
        {
            Query update = entityManager.createNativeQuery(
                """
                UPDATE {h-schema}auth_functions
                SET active = TRUE, deleted = FALSE, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE code = :value2
                    AND (NOT active OR deleted)
                """
            );

            update.setParameter("actor", actor);
            update.setParameter("value2", data.path("bootstrapFunction").asText("ADMIN_CONSOLE"));
            update.executeUpdate();
        }
        {
            Query insert = entityManager.createNativeQuery(
                """
                INSERT INTO {h-schema}auth_role_functions(role_code, function_code, created_by, updated_by)
                VALUES (:value1, :value2, :actor, :actor)
                ON CONFLICT DO NOTHING
                """
            );

            insert.setParameter("value1", data.path("bootstrapRole").asText());
            insert.setParameter("value2", data.path("bootstrapFunction").asText("ADMIN_CONSOLE"));
            insert.setParameter("actor", actor);
            insert.setParameter("actor", actor);
            insert.executeUpdate();
        }
    }
}
