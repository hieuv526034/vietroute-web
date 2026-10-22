package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Chỉ số thuộc instance đang trả lời; không chứa IP, token, URL cấu hình hoặc dữ liệu người dùng. */
@Schema(description = "Tài nguyên và lưu lượng của một instance; bộ đếm reset khi restart")
public record RuntimeStatusResponse(
    @Schema(description = "ID ngẫu nhiên của instance, đổi khi restart; không phải hostname/IP") String instanceId,
    @Schema(description = "Thời điểm UTC") Instant timestamp,
    @Schema(description = "Uptime JVM, giây") long uptimeSeconds,
    @Schema(description = "Heap đang dùng, byte; không phải tổng RAM container") long heapUsedBytes,
    @Schema(description = "Heap tối đa, byte; -1 nếu JVM không xác định") long heapMaxBytes,
    @Schema(description = "Non-heap đang dùng, byte") long nonHeapUsedBytes,
    @Schema(description = "Tải CPU tiến trình 0..1; null nếu nền tảng không hỗ trợ") Double processCpuRatio,
    @Schema(description = "Số thread JVM đang sống") int liveThreads,
    @Schema(description = "Request đang xử lý, không tính scrape/static/health") int activeRequests,
    @Schema(description = "Request đã hoàn tất từ lúc khởi động") long completedRequests,
    @Schema(description = "Tổng HTTP 5xx từ lúc khởi động") long serverErrors,
    @Schema(description = "Tổng HTTP 429 từ lúc khởi động") long rateLimitedRequests,
    @Schema(description = "Tổng HTTP 503 từ lúc khởi động") long unavailableRequests,
    @Schema(description = "Độ trễ trung bình từ lúc khởi động, ms") double meanResponseMs,
    @Schema(description = "Worker được bật và lifecycle đang chạy, không thay kiểm heartbeat") boolean workerRunning,
    @Schema(description = "Job đang chạy trên instance") int activeJobs,
    @Schema(description = "Connection đang được mượn") int activeConnections,
    @Schema(description = "Thread chờ connection") int waitingConnections,
    @Schema(description = "Probe DB định kỳ đã được bật") boolean databaseProbeEnabled,
    @Schema(description = "Kết quả probe gần nhất; null khi chưa có mẫu") Boolean databaseAvailable,
    @Schema(description = "Thời điểm probe thành công gần nhất; null khi chưa có") Instant databaseLastSuccess,
    @Schema(description = "Job QUEUED đến hạn theo mẫu DB gần nhất; null khi chưa có hoặc mẫu cuối thất bại")
    Long queuedJobs,
    @Schema(description = "Job DEAD theo mẫu DB gần nhất; null khi chưa có hoặc mẫu cuối thất bại") Long deadJobs,
    @Schema(description = "Độ trễ job đến hạn lâu nhất, giây; null khi chưa có hoặc mẫu cuối thất bại")
    Double oldestDueJobSeconds
) {}
