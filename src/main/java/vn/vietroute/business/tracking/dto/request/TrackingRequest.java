package vn.vietroute.business.tracking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Tracking; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class TrackingRequest extends BaseRequest {

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version chuyến khi tài xế chủ động bật chia sẻ")
    private Long tripVersion;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class, RequestGroups.Delete.class })
    @Schema(description = "Nonce consent của lần bật trên thiết bị; không thay JWT hoặc quyền chuyến")
    private UUID sessionKey;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @DecimalMin(value = "-90", message = "{validation.location.latitude}")
    @DecimalMax(value = "90", message = "{validation.location.latitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Vĩ độ WGS84 từ thiết bị, tối đa 6 chữ số thập phân")
    private BigDecimal latitude;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @DecimalMin(value = "-180", message = "{validation.location.longitude}")
    @DecimalMax(value = "180", message = "{validation.location.longitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Kinh độ WGS84 từ thiết bị, tối đa 6 chữ số thập phân")
    private BigDecimal longitude;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @DecimalMin(value = "0", message = "{validation.invalid}")
    @DecimalMax(value = "100000", message = "{validation.invalid}")
    @Digits(integer = 6, fraction = 2, message = "{validation.invalid}")
    @Schema(description = "Độ chính xác thiết bị báo, mét; không giả định đây là GPS chính xác")
    private BigDecimal accuracy;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Schema(description = "Thời điểm thiết bị đo UTC; không lấy giờ gửi để thay timestamp cũ")
    private Instant measuredAt;
}
