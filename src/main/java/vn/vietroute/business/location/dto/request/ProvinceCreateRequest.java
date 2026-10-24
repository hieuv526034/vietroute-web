package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.validation.BusinessCode;

@Getter
@Setter
@Schema(description = "Tạo tỉnh/thành; mã nội bộ không phải mã hành chính nhà nước")
public class ProvinceCreateRequest extends LocationNameRequest {

    @NotBlank(message = "{validation.required}")
    @BusinessCode
    @Schema(description = "Mã nội bộ bất biến, chữ hoa/số, 3–40 ký tự", example = "PROVINCE01")
    private String code;
}
