package vn.vietroute.common.monitoring.service;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.common.config.MailSettings;
import vn.vietroute.common.config.TrafficSettings;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.job.worker.JobWorker;
import vn.vietroute.common.monitoring.dto.response.SystemStatusResponse;

/** Endpoint readiness có truy vấn DB thật; tách khỏi /health để health check không đánh thức Neon. */
@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final HikariDataSource pool;

    private final JdbcConnectionFactory connections;

    private final JobWorker worker;

    private final MailSettings mail;

    private final TrafficSettings traffic;

    private final Clock clock;

    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    public SystemStatusResponse status() {
        Map<String, Long> queues = new LinkedHashMap<>();
        try (
            Connection connection = connections.open();
            PreparedStatement query = connection.prepareStatement(
                "SELECT status,count(*) FROM system_jobs GROUP BY status"
            );
            ResultSet rows = query.executeQuery()
        ) {
            while (rows.next()) {
                queues.put(rows.getString(1), rows.getLong(2));
            }
        } catch (SQLException exception) {
            throw BaseRepository.translate(exception);
        }
        var stats = pool.getHikariPoolMXBean();
        return new SystemStatusResponse(
            Instant.now(clock),
            traffic.getRedisUrl().isBlank() ? "memory" : "redis",
            worker.isRunning(),
            worker.activeCount(),
            worker.completedCount(),
            worker.failedCount(),
            stats == null ? 0 : stats.getActiveConnections(),
            stats == null ? 0 : stats.getIdleConnections(),
            stats == null ? 0 : stats.getThreadsAwaitingConnection(),
            mail.isEnabled(),
            Map.copyOf(queues)
        );
    }
}
