package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Dữ liệu cho phép sửa; code/carrier/scope và audit không thuộc payload cập nhật. */
@Getter
@Setter
@Schema(description = "Trường có thể sửa của Vehicle")
public class VehicleWriteRequest extends BaseRequest {

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
}
