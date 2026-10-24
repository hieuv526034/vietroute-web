package vn.vietroute.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.auth.dto.request.AccessChangeSearchRequest;
import vn.vietroute.auth.dto.response.AccessChangeResponse;
import vn.vietroute.auth.dto.response.AccessChangeStateResponse;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Lịch sử ghi trực tiếp PostgreSQL trong transaction caller; lỗi ghi làm rollback thay đổi quyền, không queue RAM. */
@Repository
public class AccessChangeRepository extends BaseRepository {

    private final ObjectMapper mapper;

    private final AccessSettings settings;

    public AccessChangeRepository(JdbcConnectionFactory connections, ObjectMapper mapper, AccessSettings settings) {
        super(connections);
        this.mapper = mapper;
        this.settings = settings;
    }

    /** Chỉ nhận snapshot whitelist do service dựng, không serialize entity/HTTP body/header hoặc password. */
    public void append(
        Connection connection,
        Event event,
        Target kind,
        String key,
        long actor,
        String traceId,
        AccessChangeStateResponse before,
        AccessChangeStateResponse after
    ) throws SQLException {
        if (connection.getAutoCommit()) throw new IllegalStateException(
            "Lịch sử quyền phải nằm trong transaction thay đổi"
        );
        try (
            var insert = connection.prepareStatement(
                """
                INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,trace_id,before_state,after_state,created_by,updated_by)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                """
            )
        ) {
            insert.setString(1, event.name());
            insert.setString(2, kind.name());
            insert.setString(3, key);
            insert.setLong(4, actor);
            insert.setString(5, traceId);
            insert.setString(6, json(before));
            insert.setString(7, json(after));
            insert.setLong(8, actor);
            insert.setLong(9, actor);
            insert.executeUpdate();
        }
        cleanup(connection);
    }

    /** Truy vấn projection tường minh; username chỉ join theo actor DB và endpoint ADMIN-only kiểm trước. */
    public PageResponse<AccessChangeResponse> page(AccessChangeSearchRequest search) {
        return read(connection -> {
            String filter = """
            FROM auth_access_changes c
            WHERE c.created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
              AND (? = '' OR c.event_type = ?)
              AND c.target_key LIKE ? ESCAPE '!'
              AND c.actor_id = COALESCE(?, c.actor_id)
            """;
            long total;
            try (var count = connection.prepareStatement("SELECT count(*) " + filter)) {
                bind(count, search);
                try (var rows = count.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            List<AccessChangeResponse> values = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT c.id,c.event_type,c.target_kind,c.target_key,c.actor_id,(SELECT username FROM auth_users WHERE id=c.actor_id),c.trace_id,c.created_at,c.before_state::text,c.after_state::text " +
                        filter +
                        " ORDER BY c.id DESC LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search);
                query.setInt(6, search.getSize());
                query.setLong(7, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next())
                        values.add(
                            new AccessChangeResponse(
                                rows.getLong(1),
                                Event.valueOf(rows.getString(2)),
                                Target.valueOf(rows.getString(3)),
                                rows.getString(4),
                                rows.getLong(5),
                                rows.getString(6),
                                rows.getString(7),
                                rows.getTimestamp(8).toInstant(),
                                state(rows.getString(9)),
                                state(rows.getString(10))
                            )
                        );
                }
            }
            return PageResponse.of(values, search, total);
        });
    }

    /** Dọn tối đa 100 sự kiện/lần, giữ transaction nhỏ; chỉ caller đang có transaction được gọi hàm này. */
    public void cleanup(Connection connection) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
            lock.setLong(1, AccessAuditConstants.CLEANUP_LOCK);
            try (var rows = lock.executeQuery()) {
                rows.next();
                if (!rows.getBoolean(1)) return;
            }
        }
        try (
            var delete = connection.prepareStatement(
                """
                DELETE FROM auth_access_changes WHERE id IN (
                    SELECT id FROM auth_access_changes
                    WHERE created_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                       OR id <= (SELECT id FROM auth_access_changes ORDER BY id DESC OFFSET ? LIMIT 1)
                    ORDER BY id LIMIT 100
                )
                """
            )
        ) {
            delete.setInt(1, settings.getAuditRetentionDays());
            delete.setInt(2, settings.getAuditCapacity());
            delete.executeUpdate();
        }
    }

    private void bind(PreparedStatement query, AccessChangeSearchRequest search) throws SQLException {
        query.setInt(1, settings.getAuditRetentionDays());
        String event = search.getEvent() == null ? "" : search.getEvent().name();
        query.setString(2, event);
        query.setString(3, event);
        String target = search.getTarget() == null ? "" : search.getTarget().strip();
        query.setString(4, target.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        query.setObject(5, search.getActorId(), Types.BIGINT);
    }

    private String json(AccessChangeStateResponse value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private AccessChangeStateResponse state(String value) {
        try {
            return mapper.readValue(value, AccessChangeStateResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
