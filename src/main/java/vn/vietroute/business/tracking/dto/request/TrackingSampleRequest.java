package vn.vietroute.business.tracking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Chỉ nhận đo đạc và nonce của phiên; trip/actor/source/receivedAt do server quyết định. */
public record TrackingSampleRequest(
    @NotNull(message = "{validation.required}") @Schema(description = "Nonce của consent hiện hành") UUID sessionKey,
    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-90", message = "{validation.location.latitude}")
    @DecimalMax(value = "90", message = "{validation.location.latitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Vĩ độ WGS84 từ thiết bị, tối đa 6 chữ số thập phân")
    BigDecimal latitude,
    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-180", message = "{validation.location.longitude}")
    @DecimalMax(value = "180", message = "{validation.location.longitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Kinh độ WGS84 từ thiết bị, tối đa 6 chữ số thập phân")
    BigDecimal longitude,
    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "0", message = "{validation.invalid}")
    @DecimalMax(value = "100000", message = "{validation.invalid}")
    @Digits(integer = 6, fraction = 2, message = "{validation.invalid}")
    @Schema(description = "Độ chính xác thiết bị báo, mét; không giả định đây là GPS chính xác")
    BigDecimal accuracy,
    @NotNull(message = "{validation.required}")
    @Schema(description = "Thời điểm thiết bị đo UTC; không lấy giờ gửi để thay timestamp cũ")
    Instant measuredAt
) {}
