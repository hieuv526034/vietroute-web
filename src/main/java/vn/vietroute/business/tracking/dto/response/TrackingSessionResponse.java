package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Chỉ trả nonce cho tài xế sau POST bật; không lưu browser storage hoặc trả qua API viewer. */
public record TrackingSessionResponse(
    @Schema(description = "Nonce consent mới, thu hồi nonce cũ khi bật lại") UUID sessionKey,
    @Schema(description = "Thời điểm bắt đầu UTC") Instant startedAt,
    @Schema(description = "Tần suất gửi tối thiểu, giây") int minSampleSeconds,
    @Schema(description = "Lease không có mẫu mới hết hiệu lực sau số giây này") int leaseSeconds
) {}
