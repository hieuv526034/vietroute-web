package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Whitelist kiểu dữ liệu cụ thể; không serialize Settings, System.getenv hoặc map cấu hình tùy ý. */
@Schema(description = "Cấu hình không nhạy cảm tại thời điểm startup; chỉ đọc, không chứa secret/URL/mailbox")
public record SystemConfigurationResponse(
    @Schema(description = "Thời điểm đọc UTC") Instant timestamp,
    @Schema(description = "Đây là snapshot chỉ đọc; thay env cần restart/deploy") boolean readOnly,
    @Schema(description = "Môi trường và bảo mật cookie") Application application,
    @Schema(description = "Giới hạn connection và timeout") Database database,
    @Schema(description = "Quota và tài nguyên request theo node") Traffic traffic,
    @Schema(description = "Cấu hình worker") Worker worker,
    @Schema(description = "Trạng thái chức năng provider, không phải kết quả gọi thử provider") Features features,
    @Schema(description = "Cấu hình giám sát") Monitoring monitoring
) {
    @Schema(description = "Môi trường ứng dụng; không trả key JWT hoặc tài khoản bootstrap")
    public record Application(
        @Schema(description = "Production hay development") boolean production,
        @Schema(description = "Cookie yêu cầu HTTPS") boolean secureCookie,
        @Schema(description = "Initializer được bật ở startup") boolean initializationEnabled,
        @Schema(description = "Thời hạn access token, giây") long accessTokenSeconds
    ) {}

    @Schema(description = "Giới hạn DB, không chứa JDBC URL/username/password")
    public record Database(
        @Schema(description = "Số connection tối đa mỗi node") int maximumPoolSize,
        @Schema(description = "Timeout mượn connection, ms") int acquisitionTimeoutMs,
        @Schema(description = "Timeout kết nối DB, giây") int connectTimeoutSeconds,
        @Schema(description = "Timeout đọc socket, giây") int socketTimeoutSeconds
    ) {}

    @Schema(description = "Quota không đồng nghĩa số người online")
    public record Traffic(
        @Schema(description = "Quota request/phút/IP") int requestsPerMinute,
        @Schema(description = "Quota login/phút/IP") int loginsPerMinute,
        @Schema(description = "Request được xử lý đồng thời mỗi node") int maxConcurrent,
        @Schema(description = "Login được xử lý đồng thời mỗi node") int maxConcurrentLogins,
        @Schema(description = "Giới hạn body JSON, byte") int maxBodyBytes,
        @Schema(description = "Số key quota tối đa khi dùng memory") int maxKeys,
        @Schema(description = "memory hoặc redis; không trả địa chỉ backend") String quotaBackend,
        @Schema(description = "Đã khai báo trusted proxy; không trả CIDR") boolean trustedProxyConfigured
    ) {}

    @Schema(description = "Settings worker, khác trạng thái chạy thực tế ở runtime endpoint")
    public record Worker(
        @Schema(description = "Worker được bật") boolean enabled,
        @Schema(description = "Số luồng handler") int threads,
        @Schema(description = "Chu kỳ poll, giây") int pollSeconds,
        @Schema(description = "Thời hạn lease, giây") int leaseSeconds,
        @Schema(description = "Retention receipt SUCCEEDED, ngày") int retentionDays
    ) {}

    @Schema(description = "Cờ tính năng; enabled không chứng minh provider đang khả dụng")
    public record Features(
        @Schema(description = "Mail đã bật") boolean mailEnabled,
        @Schema(description = "gmail hoặc smtp") String mailProvider,
        @Schema(description = "Cloudinary public media đã bật") boolean mediaEnabled,
        @Schema(description = "Giới hạn file upload, byte") int maxUploadBytes,
        @Schema(description = "Cho phép chia sẻ vị trí; vẫn cần tài xế chủ động consent") boolean trackingEnabled,
        @Schema(description = "Google Maps chưa cấu hình, không có provider/key được công bố") String mapStatus
    ) {}

    @Schema(description = "Không trả token metrics; credentials đặt ngoài UI")
    public record Monitoring(
        @Schema(description = "Endpoint scrape được bật bằng token") boolean scrapeEnabled,
        @Schema(description = "Probe DB được bật") boolean databaseProbeEnabled,
        @Schema(description = "Chu kỳ probe DB, giây") int pollSeconds,
        @Schema(description = "Ghi hiện diện/bộ đếm request vào DB được bật") boolean activityEnabled,
        @Schema(description = "Ngưỡng request/phút/chủ thể để phát tín hiệu") int activityRequestThreshold
    ) {}
}
