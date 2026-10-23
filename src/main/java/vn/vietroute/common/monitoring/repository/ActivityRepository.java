package vn.vietroute.common.monitoring.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.monitoring.dto.response.ActivitySnapshot;
import vn.vietroute.common.monitoring.dto.response.OnlineVisitorResponse;
import vn.vietroute.common.monitoring.dto.response.RequestAnomalyResponse;

/** Dùng chung pool quan sát tối đa một connection/node với journal, transaction ngắn, không queue trong RAM. */
@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private final ApiJournalDatabase database;

    private final Clock clock;

    /** Khóa thử toàn cụm, không chờ khi quá tải; quota hàng cứng để không làm đầy Neon Free. */
    public boolean record(String visitor, String subject, Long user, int status) {
        // Hàm DB lấy khóa trước lệnh ghi, giữ quota giữa node và giảm còn một lần gọi mạng.
        try (
            Connection connection = database.open();
            PreparedStatement query = timed(connection, "SELECT record_system_activity(?,?,?,?)")
        ) {
            query.setString(1, visitor);
            query.setString(2, subject);
            query.setObject(3, user);
            query.setInt(4, status);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    /** Cleanup nhỏ và khóa riêng, node bất kỳ dọn được; không xóa dữ liệu nghiệp vụ. */
    public void cleanup() {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement timeout = timed(connection, "SET LOCAL statement_timeout = '150ms'")) {
                timeout.execute();
            }
            try (
                PreparedStatement lock = timed(connection, "SELECT pg_try_advisory_xact_lock(781205616)");
                ResultSet row = lock.executeQuery()
            ) {
                row.next();
                if (!row.getBoolean(1)) {
                    connection.rollback();
                    return;
                }
                try (
                    PreparedStatement query = timed(
                        connection,
                        "DELETE FROM system_visitor_presence WHERE visitor_hash IN (SELECT visitor_hash FROM system_visitor_presence WHERE last_seen<CURRENT_TIMESTAMP-INTERVAL '7 days' OR (last_seen<CURRENT_TIMESTAMP-INTERVAL '3 minutes' AND (SELECT count(*) FROM system_visitor_presence)>=9800) ORDER BY last_seen LIMIT 200)"
                    )
                ) {
                    query.executeUpdate();
                }
                try (
                    PreparedStatement query = timed(
                        connection,
                        "DELETE FROM system_activity_buckets WHERE (subject_hash,bucket_at) IN (SELECT subject_hash,bucket_at FROM system_activity_buckets WHERE bucket_at<CURRENT_TIMESTAMP-INTERVAL '60 minutes' ORDER BY bucket_at LIMIT 500)"
                    )
                ) {
                    query.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    public ActivitySnapshot snapshot(long missed) {
        try (
            Connection connection = database.open();
            PreparedStatement query = timed(
                connection,
                "SELECT count(DISTINCT user_id) FILTER(WHERE last_seen>CURRENT_TIMESTAMP-INTERVAL '3 minutes'),count(*) FILTER(WHERE last_seen>CURRENT_TIMESTAMP-INTERVAL '3 minutes'),count(*) FILTER(WHERE user_id IS NULL AND last_seen>CURRENT_TIMESTAMP-INTERVAL '3 minutes'),count(*) FILTER(WHERE last_seen>CURRENT_TIMESTAMP-INTERVAL '24 hours') FROM system_visitor_presence"
            );
            ResultSet row = query.executeQuery()
        ) {
            row.next();
            long users = row.getLong(1),
                browsers = row.getLong(2),
                guests = row.getLong(3),
                visitors = row.getLong(4);
            try (
                PreparedStatement totals = timed(
                    connection,
                    "SELECT COALESCE(sum(requests),0),COALESCE(sum(denied),0),COALESCE(sum(limited),0),COALESCE(sum(failures),0) FROM system_activity_buckets WHERE bucket_at>CURRENT_TIMESTAMP-INTERVAL '60 minutes'"
                );
                ResultSet result = totals.executeQuery()
            ) {
                result.next();
                return new ActivitySnapshot(
                    true,
                    users,
                    browsers,
                    guests,
                    visitors,
                    result.getLong(1),
                    result.getLong(2),
                    result.getLong(3),
                    result.getLong(4),
                    missed,
                    clock.instant()
                );
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    public PageResponse<OnlineVisitorResponse> online(PageRequest page) {
        try (Connection connection = database.open()) {
            long count;
            try (
                PreparedStatement query = timed(
                    connection,
                    "SELECT count(*) FROM system_visitor_presence WHERE last_seen>CURRENT_TIMESTAMP-INTERVAL '3 minutes'"
                );
                ResultSet row = query.executeQuery()
            ) {
                row.next();
                count = row.getLong(1);
            }
            List<OnlineVisitorResponse> content = new ArrayList<>();
            try (
                PreparedStatement query = timed(
                    connection,
                    "SELECT p.user_id,u.username,left(p.visitor_hash,12),p.first_seen,p.last_seen FROM system_visitor_presence p LEFT JOIN auth_users u ON u.id=p.user_id WHERE p.last_seen>CURRENT_TIMESTAMP-INTERVAL '3 minutes' ORDER BY p.last_seen DESC,p.visitor_hash LIMIT ? OFFSET ?"
                )
            ) {
                query.setInt(1, page.getSize());
                query.setLong(2, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        content.add(
                            new OnlineVisitorResponse(
                                rows.getObject(1, Long.class),
                                rows.getString(2),
                                rows.getString(3),
                                rows.getTimestamp(4).toInstant(),
                                rows.getTimestamp(5).toInstant()
                            )
                        );
                    }
                }
            }
            return PageResponse.of(content, page, count);
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    public List<RequestAnomalyResponse> anomalies(int threshold) {
        try (
            Connection connection = database.open();
            PreparedStatement query = timed(
                connection,
                "SELECT user_id,left(subject_hash,12),bucket_at,requests,denied,limited,failures FROM system_activity_buckets WHERE bucket_at>CURRENT_TIMESTAMP-INTERVAL '5 minutes' AND (requests>=? OR denied>=20 OR limited>=10 OR failures>=10) ORDER BY bucket_at DESC,requests DESC,subject_hash LIMIT 100"
            )
        ) {
            query.setInt(1, threshold);
            List<RequestAnomalyResponse> result = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(
                        new RequestAnomalyResponse(
                            rows.getObject(1, Long.class),
                            rows.getString(2),
                            rows.getTimestamp(3).toInstant(),
                            rows.getLong(4),
                            rows.getLong(5),
                            rows.getLong(6),
                            rows.getLong(7)
                        )
                    );
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
    }

    private static PreparedStatement timed(Connection connection, String sql) throws SQLException {
        PreparedStatement query = connection.prepareStatement(sql);
        query.setQueryTimeout(1);
        return query;
    }
}
