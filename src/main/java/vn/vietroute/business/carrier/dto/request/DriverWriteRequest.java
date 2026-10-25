package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Dữ liệu cho phép sửa; code/carrier/scope và audit không thuộc payload cập nhật. */
@Getter
@Setter
@Schema(description = "Trường có thể sửa của Driver")
public class DriverWriteRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Họ tên tài xế")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại liên hệ")
    private String phone;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Tài khoản DRIVER có membership đúng nhà xe; null khi chưa gắn account")
    private Long userId;

    @Schema(description = "Hồ sơ đang hoạt động")
    private boolean active = true;
}
