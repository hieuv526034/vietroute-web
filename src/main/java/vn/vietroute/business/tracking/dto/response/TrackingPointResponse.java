package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Chỉ viewer đã kiểm owner/assigned/scope mới được nhận mẫu vị trí. */
public record TrackingPointResponse(
    @Schema(description = "Vĩ độ thiết bị báo") BigDecimal latitude,
    @Schema(description = "Kinh độ thiết bị báo") BigDecimal longitude,
    @Schema(description = "Độ chính xác, mét") BigDecimal accuracy,
    @Schema(description = "Thời điểm thiết bị đo UTC") Instant measuredAt,
    @Schema(description = "Thời điểm server nhận UTC") Instant receivedAt
) {}
