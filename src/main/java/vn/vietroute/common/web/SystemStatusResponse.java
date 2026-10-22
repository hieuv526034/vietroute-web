package vn.vietroute.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Tình trạng instance, chỉ SYSTEM_READ; không chứa URL/secret/email")
public record SystemStatusResponse(
    @Schema(description = "Thời điểm UTC") Instant timestamp,
    @Schema(description = "Quota dùng chung Redis hay memory của instance") String rateLimitBackend,
    @Schema(description = "Worker đang chạy") boolean workerRunning,
    @Schema(description = "Số handler đang chiếm slot") int activeJobs,
    @Schema(description = "Job thành công trên instance từ lần khởi động") long completedJobs,
    @Schema(description = "Lần xử lý thất bại trên instance") long failedAttempts,
    @Schema(description = "Số connection đang mượn") int activeConnections,
    @Schema(description = "Số connection rảnh") int idleConnections,
    @Schema(description = "Số thread chờ pool") int waitingConnections,
    @Schema(description = "Email được bật") boolean mailEnabled,
    @Schema(description = "Số job mỗi trạng thái trong DB") Map<String, Long> queues
) {}
