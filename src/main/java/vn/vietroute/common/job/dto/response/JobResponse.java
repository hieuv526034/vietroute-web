package vn.vietroute.common.job.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Trạng thái công việc nền, không chứa payload/email/secret")
public record JobResponse(
    @Schema(description = "ID công việc") UUID id,
    @Schema(description = "Loại handler") String type,
    @Schema(description = "QUEUED, RUNNING, SUCCEEDED hoặc DEAD") String status,
    @Schema(description = "Số lần đã bắt đầu chạy") int attempts,
    @Schema(description = "Giới hạn retry") int maxAttempts,
    @Schema(description = "Thời điểm được nhận việc") Instant availableAt,
    @Schema(description = "Thời hạn lease, null nếu không chạy") Instant lockedUntil,
    @Schema(description = "Mã lỗi đã làm sạch, không chứa thông báo provider") String lastErrorCode,
    @Schema(description = "Thời điểm kết thúc") Instant finishedAt,
    @Schema(description = "Thời điểm tạo") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật") Instant updatedAt,
    @Schema(description = "Người tạo") Long createdBy,
    @Schema(description = "Version optimistic retry") long version
) {}
