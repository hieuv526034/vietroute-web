package vn.vietroute.common.monitoring.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalEntry;

/** JDBC thuần, chỉ lưu projection đã lọc; mọi giá trị bind, SQL không gắn schema cố định. */
@Repository
@RequiredArgsConstructor
public class ApiJournalRepository {

    private final ApiJournalDatabase database;

    private final ObjectMapper mapper;

    private static final String FILTER = """
    WHERE recorded_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute')
      AND (? = '' OR trace_id = ?) AND strpos(endpoint, ?) > 0
      AND (? = '' OR method = ?) AND (? = 0 OR http_status = ?)
      AND duration_ms >= ? AND (? = '' OR code = ?) AND (NOT ? OR http_status >= 400)
      AND (? = 0 OR request_user_id = ?)
    """;

    private static final String METADATA = """
    id,started_at,instance_id,trace_id,method,endpoint,controller,http_status,
    duration_ms,code,message_key,diagnostic,sql_state,request_user_id,
    (SELECT u.username FROM auth_users u WHERE u.id=api_request_journal.request_user_id) AS request_username
    """;

    /** Autocommit chỉ thành công khi PostgreSQL xác nhận insert; không giữ payload hoặc batch trong bộ nhớ. */
    public void append(ApiJournalDetail detail) {
        try (
            Connection connection = database.open();
            PreparedStatement query = timed(
                connection,
                """
                INSERT INTO api_request_journal(id,started_at,instance_id,trace_id,method,endpoint,controller,
                    http_status,duration_ms,code,message_key,diagnostic,sql_state,replay_path,query_json,
                    request_body,response_body,content_kind,request_user_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)
                """
            )
        ) {
            var e = detail.entry();
            query.setObject(1, e.id());
            query.setTimestamp(2, Timestamp.from(e.timestamp()));
            query.setString(3, detail.instanceId());
            query.setString(4, e.traceId());
            query.setString(5, e.method());
            query.setString(6, e.endpoint());
            query.setString(7, e.controller());
            query.setInt(8, e.status());
            query.setLong(9, e.durationMs());
            query.setString(10, e.code());
            query.setString(11, e.messageKey());
            query.setString(12, e.diagnostic());
            query.setString(13, e.sqlState());
            query.setString(14, detail.replayPath());
            query.setString(15, mapper.writeValueAsString(detail.query()));
            query.setString(16, detail.requestBody());
            query.setString(17, detail.responseBody());
            query.setString(18, detail.contentKind());
            query.setObject(19, e.userId());
            query.executeUpdate();
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode filtered journal query", exception);
        }
    }

    /** Chỉ đọc metadata của trang hiện tại, không tải tất cả snapshot JSON vào RAM để lọc. */
    public PageResponse<ApiJournalEntry> page(ApiJournalSearchRequest search, int minutes) {
        try (Connection connection = database.open()) {
            long total;
            try (PreparedStatement query = timed(connection, "SELECT count(*) FROM api_request_journal " + FILTER)) {
                bind(query, search, minutes);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var entries = new ArrayList<ApiJournalEntry>();
            try (
                PreparedStatement query = timed(
                    connection,
                    "SELECT " +
                        METADATA +
                        " FROM api_request_journal " +
                        FILTER +
                        " ORDER BY recorded_at DESC,id DESC LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search, minutes);
                query.setInt(15, search.getSize());
                query.setLong(16, search.offset());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) entries.add(entry(row));
                }
            }
            return PageResponse.of(entries, search, total);
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    public ApiJournalDetail find(UUID id, int minutes) {
        try (
            Connection connection = database.open();
            PreparedStatement query = timed(
                connection,
                """
                SELECT *, (SELECT u.username FROM auth_users u WHERE u.id=api_request_journal.request_user_id) AS request_username FROM api_request_journal WHERE id=?
                    AND recorded_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute')
                """
            )
        ) {
            query.setObject(1, id);
            query.setInt(2, minutes);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                Map<String, String> params = mapper.readValue(
                    row.getString("query_json"),
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                );
                return new ApiJournalDetail(
                    entry(row),
                    row.getString("instance_id"),
                    row.getString("replay_path"),
                    Map.copyOf(params),
                    row.getString("request_body"),
                    row.getString("response_body"),
                    row.getString("content_kind")
                );
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot decode filtered journal query", exception);
        }
    }

    /** Khóa advisory dùng chung toàn DB/schema, mỗi lượt xóa tối đa 500 hết hạn + 500 vượt mục tiêu. */
    public void cleanup(int minutes, int capacity) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (
                    PreparedStatement lock = timed(connection, "SELECT pg_try_advisory_xact_lock(781205615)");
                    ResultSet row = lock.executeQuery()
                ) {
                    row.next();
                    if (!row.getBoolean(1)) {
                        connection.rollback();
                        return;
                    }
                }
                try (
                    PreparedStatement query = timed(
                        connection,
                        """
                        DELETE FROM api_request_journal WHERE id IN (
                            SELECT id FROM api_request_journal
                            WHERE recorded_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 minute')
                            ORDER BY recorded_at,id LIMIT 500)
                        """
                    )
                ) {
                    query.setInt(1, minutes);
                    query.executeUpdate();
                }
                try (
                    PreparedStatement query = timed(
                        connection,
                        """
                        DELETE FROM api_request_journal WHERE id IN (
                            SELECT id FROM api_request_journal ORDER BY recorded_at DESC,id DESC OFFSET ? LIMIT 500)
                        """
                    )
                ) {
                    query.setInt(1, capacity);
                    query.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    exception.addSuppressed(rollback);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    private static PreparedStatement timed(Connection connection, String sql) throws SQLException {
        PreparedStatement query = connection.prepareStatement(sql);
        query.setQueryTimeout(1);
        return query;
    }

    private static void bind(PreparedStatement query, ApiJournalSearchRequest search, int minutes) throws SQLException {
        query.setInt(1, minutes);
        query.setString(2, search.getTraceId());
        query.setString(3, search.getTraceId());
        query.setString(4, search.getEndpoint());
        query.setString(5, search.getMethod());
        query.setString(6, search.getMethod());
        query.setInt(7, search.getStatus());
        query.setInt(8, search.getStatus());
        query.setLong(9, search.getMinDurationMs());
        query.setString(10, search.getCode());
        query.setString(11, search.getCode());
        query.setBoolean(12, search.isErrorsOnly());
        query.setLong(13, search.getUserId());
        query.setLong(14, search.getUserId());
    }

    private static ApiJournalEntry entry(ResultSet row) throws SQLException {
        return new ApiJournalEntry(
            row.getObject("id", UUID.class),
            row.getTimestamp("started_at").toInstant(),
            row.getString("trace_id"),
            row.getString("method"),
            row.getString("endpoint"),
            row.getString("controller"),
            row.getInt("http_status"),
            row.getLong("duration_ms"),
            row.getString("code"),
            row.getString("message_key"),
            row.getString("diagnostic"),
            row.getString("sql_state"),
            row.getObject("request_user_id", Long.class),
            row.getString("request_username")
        );
    }
}
