package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ viewer đã kiểm owner/assigned/scope mới được nhận mẫu vị trí. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrackingPointResponse {

    @Schema(description = "Vĩ độ thiết bị báo")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ thiết bị báo")
    private BigDecimal longitude;

    @Schema(description = "Độ chính xác, mét")
    private BigDecimal accuracy;

    @Schema(description = "Thời điểm thiết bị đo UTC")
    private Instant measuredAt;

    @Schema(description = "Thời điểm server nhận UTC")
    private Instant receivedAt;
}
