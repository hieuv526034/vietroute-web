package vn.vietroute.auth.repo.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ. */
@Repository
public class AccessChangeCustomRepo extends BaseRepository {

    public AccessChangeCustomRepo(JdbcConnectionFactory connections) {
        super(connections);
    }

    private static final String INSERT = """
    INSERT INTO auth_access_changes(event_type, target_kind, target_key, actor_id, trace_id,
        before_state, after_state, created_by, updated_by)
    VALUES (?, ?, ?, ?, ?, ?::JSONB, ?::JSONB, ?, ?)
    """;

    private static final ObjectMapper JSON = new ObjectMapper();

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
        return read(AccessChangeCustomRepo::cleanup);
    }

    /** Dọn một lô trong transaction startup/nhật ký, không thêm query vào từng mutation. */
    public static int cleanup(Connection connection) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT PG_TRY_ADVISORY_XACT_LOCK(781205618)")) {
            try (ResultSet row = lock.executeQuery()) {
                row.next();
                if (!row.getBoolean(1)) return 0;
            }
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                DELETE
                FROM auth_access_changes
                WHERE id IN (
                    SELECT id
                    FROM auth_access_changes
                    WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
                        OR id < (
                            SELECT COALESCE(MAX(id), 0) - 1000
                            FROM auth_access_changes
                        )
                    ORDER BY id
                    LIMIT 200
                )
                """
            )
        ) {
            return query.executeUpdate();
        }
    }
}
