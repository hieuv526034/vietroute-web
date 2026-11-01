package vn.vietroute.auth.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng AccessChange; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class AccessChangeRepo extends JpaRepositorySupport<AccessChange, Long> {

    private static final String INSERT =
        "INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,trace_id,before_state,after_state,created_by,updated_by) VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?,?)";

    private static final ObjectMapper JSON = new ObjectMapper();

    public AccessChangeRepo(EntityManager manager, JdbcTransactions transactions) {
        super(AccessChange.class, manager, transactions);
    }

    public void append(
        Connection connection,
        String event,
        String kind,
        String key,
        long actor,
        Map<String, Object> before,
        Map<String, Object> after
    ) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(INSERT)) {
            bind(query, event, kind, key, actor, before, after);
            query.executeUpdate();
        }
    }

    /** Thu hồi nhiều membership ghi một batch, không truy vấn hoặc execute mỗi dòng. */
    public void appendMemberships(
        Connection connection,
        long user,
        List<RoleDTO> before,
        List<RoleDTO> after,
        long actor
    ) throws SQLException {
        Map<Long, RoleDTO> current = new HashMap<>();
        after.forEach(value -> current.put(value.getScopeId(), value));
        try (PreparedStatement query = connection.prepareStatement(INSERT)) {
            boolean changed = false;
            for (RoleDTO previous : before) {
                RoleDTO next = current.get(previous.getScopeId());
                if (next != null && previous.getRoles().equals(next.getRoles())) continue;
                bind(
                    query,
                    "SCOPE_MEMBERSHIP",
                    "MEMBERSHIP",
                    previous.getScopeId() + ":" + user,
                    actor,
                    Map.of("roles", previous.getRoles(), "version", previous.getVersion()),
                    Map.of(
                        "roles",
                        next == null ? Set.of() : next.getRoles(),
                        "version",
                        next == null ? previous.getVersion() + 1 : next.getVersion()
                    )
                );
                query.addBatch();
                changed = true;
            }
            if (changed) query.executeBatch();
        }
    }

    private void bind(
        PreparedStatement query,
        String event,
        String kind,
        String key,
        long actor,
        Map<String, Object> before,
        Map<String, Object> after
    ) throws SQLException {
        String trace = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object value = attributes.getRequest().getAttribute(Http.TRACE_ATTRIBUTE);
            if (value instanceof String text) trace = text;
        }
        query.setString(1, event);
        query.setString(2, kind);
        query.setString(3, key);
        query.setLong(4, actor);
        query.setString(5, trace);
        try {
            query.setString(6, JSON.writeValueAsString(before));
            query.setString(7, JSON.writeValueAsString(after));
        } catch (JsonProcessingException error) {
            throw new SQLException("Không ghi được projection lịch sử", "22000", error);
        }
        query.setLong(8, actor);
        query.setLong(9, actor);
    }

    public int cleanup() {
        return read(connection -> {
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(781205618)")) {
                try (ResultSet row = lock.executeQuery()) {
                    row.next();
                    if (!row.getBoolean(1)) return 0;
                }
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    "DELETE FROM auth_access_changes WHERE id IN(SELECT id FROM auth_access_changes WHERE created_at<CURRENT_TIMESTAMP-INTERVAL '7 days' OR id<(SELECT COALESCE(max(id),0)-1000 FROM auth_access_changes) ORDER BY id LIMIT 200)"
                )
            ) {
                return query.executeUpdate();
            }
        });
    }
}
