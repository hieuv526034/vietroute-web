package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.groups.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Vehicle; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class VehicleRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.VEHICLE_PLATE, message = "{validation.invalid}")
    @Schema(description = "Biển số; server chuẩn hóa chữ hoa và DB loại trừ trùng khác dấu phân cách")
    private String plateNumber;

    @NotNull(message = "{validation.required}")
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID loại xe đang hoạt động")
    private Long vehicleTypeId;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Số chỗ có thể bán, 1–200")
    private int capacity;

    @Schema(description = "Xe đang hoạt động")
    private boolean active = true;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID ảnh READY của chính actor hoặc null")
    private Long photoMediaId;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã nghiệp vụ chữ hoa/số 3–40 ký tự, bắt đầu bằng chữ")
    private String code;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID nhà xe; server kiểm membership và scope")
    private Long carrierId;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class })
    @Schema(description = "Version đã đọc mới nhất", minimum = "0")
    private Long version;
}
