package vn.vietroute.common.monitoring.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.config.integration.JobSettings;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.config.monitoring.ActivitySettings;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.constant.AccessConstants.Groups;
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

    private final TrackingSettings tracking;

    private final MonitoringSettings monitoring;

    private final ActivitySettings activity;

    private final Clock clock;

    /** Đọc cấu hình startup không cần DB, không serialize trực tiếp bất kỳ Settings nào. */
    @AccessOperation(resource = Groups.CONFIGURATION, action = PermissionAction.READ)
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
            new Features(
                mail.isEnabled(),
                mail.getProvider(),
                media.isEnabled(),
                MediaSettings.MAX_FILE_BYTES,
                tracking.isEnabled(),
                "UNCONFIGURED"
            ),
            new Monitoring(
                monitoring.isScrapeEnabled(),
                monitoring.isDatabaseEnabled(),
                monitoring.getPollSeconds(),
                activity.isEnabled(),
                activity.getRequestThreshold()
            )
        );
    }
}
