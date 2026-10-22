package vn.vietroute.common.monitoring.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Đọc tổng hợp có timeout, không payload/PII; không kết nối DB nếu probe chưa được bật. */
@Repository
public class MonitoringRepository {

    private final JdbcConnectionFactory connections;

    public MonitoringRepository(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    /** Một query cho trạng thái DB và queue; chỉ job đã đến hạn mới tính lag. */
    public QueueSample sample() throws SQLException {
        try (
            Connection connection = connections.open();
            PreparedStatement query = connection.prepareStatement(
                "SELECT CURRENT_TIMESTAMP, count(*) FILTER (WHERE status='QUEUED' AND available_at<=CURRENT_TIMESTAMP), " +
                    "count(*) FILTER (WHERE status='DEAD'), " +
                    "COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-min(available_at) FILTER (WHERE status='QUEUED' AND available_at<=CURRENT_TIMESTAMP)),0) FROM system_jobs"
            )
        ) {
            query.setQueryTimeout(3);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return new QueueSample(
                    rows.getTimestamp(1).toInstant(),
                    rows.getLong(2),
                    rows.getLong(3),
                    Math.max(0, rows.getDouble(4))
                );
            }
        }
    }

    /** Mẫu bất biến để luồng scrape không cần giữ khóa hoặc query thêm. */
    public record QueueSample(Instant timestamp, long queued, long dead, double oldestDueSeconds) {}
}
