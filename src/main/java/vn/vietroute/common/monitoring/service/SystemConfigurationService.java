package vn.vietroute.common.monitoring.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.common.config.AppSettings;
import vn.vietroute.common.config.DatabaseSettings;
import vn.vietroute.common.config.JobSettings;
import vn.vietroute.common.config.MailSettings;
import vn.vietroute.common.config.MediaSettings;
import vn.vietroute.common.config.MonitoringSettings;
import vn.vietroute.common.config.TrafficSettings;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Application;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Database;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Features;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Monitoring;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Traffic;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse.Worker;

/** Chọn từng trường an toàn để công bố; không có API ghi env, thử kết nối hay đọc file secret. */
@Service
@RequiredArgsConstructor
public class SystemConfigurationService {

    private final AppSettings app;

    private final DatabaseSettings database;

    private final TrafficSettings traffic;

    private final JobSettings worker;

    private final MailSettings mail;

    private final MediaSettings media;

    private final MonitoringSettings monitoring;

    private final Clock clock;

    /** Đọc cấu hình startup không cần DB, không serialize trực tiếp bất kỳ Settings nào. */
    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    public SystemConfigurationResponse read() {
        return new SystemConfigurationResponse(
            Instant.now(clock),
            true,
            new Application(
                app.isProduction(),
                app.isCookieSecure(),
                app.isDatabaseInitEnabled(),
                app.getAccessTokenLifetime().toSeconds()
            ),
            new Database(
                database.getMaximumPoolSize(),
                database.getAcquisitionTimeoutMs(),
                database.getConnectTimeoutSeconds(),
                database.getSocketTimeoutSeconds()
            ),
            new Traffic(
                traffic.getRequestsPerMinute(),
                traffic.getLoginsPerMinute(),
                traffic.getMaxConcurrent(),
                traffic.getMaxConcurrentLogins(),
                traffic.getMaxBodyBytes(),
                traffic.getMaxKeys(),
                traffic.getRedisUrl().isBlank() ? "memory" : "redis",
                !traffic.getProxyCidrs().isBlank()
            ),
            new Worker(
                worker.isEnabled(),
                worker.getThreads(),
                worker.getPollSeconds(),
                worker.getLeaseSeconds(),
                worker.getRetentionDays()
            ),
            new Features(mail.isEnabled(), mail.getProvider(), media.isEnabled(), MediaSettings.MAX_FILE_BYTES),
            new Monitoring(monitoring.isScrapeEnabled(), monitoring.isDatabaseEnabled(), monitoring.getPollSeconds())
        );
    }
}
