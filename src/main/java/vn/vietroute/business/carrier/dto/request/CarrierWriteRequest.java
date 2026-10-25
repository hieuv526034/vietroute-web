package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Dữ liệu cho phép sửa; code/carrier/scope và audit không thuộc payload cập nhật. */
@Getter
@Setter
@Schema(description = "Trường có thể sửa của Carrier")
public class CarrierWriteRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên thương mại nhà xe")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại công bố")
    private String phone;

    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Mô tả nhà xe")
    private String description = "";

    @Schema(description = "Đang nhận nghiệp vụ mới")
    private boolean active = true;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID ảnh logo READY do chính actor sở hữu, null để gỡ")
    private Long logoMediaId;
}
