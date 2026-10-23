package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description = "Chủ thể vượt ngưỡng theo phút trong 5 phút gần nhất; tín hiệu xem xét, không tự kết luận tấn công"
)
public record RequestAnomalyResponse(
    @Schema(description = "ID user từ principal xác thực, null là nguồn khách") Long userId,
    @Schema(description = "Nguồn đã băm, không chứa IP thô") String subject,
    @Schema(description = "Phút xảy ra tín hiệu") Instant minute,
    @Schema(description = "Số request/phút") long requests,
    @Schema(description = "401/403") long denied,
    @Schema(description = "429") long limited,
    @Schema(description = "5xx") long failures
) {}
