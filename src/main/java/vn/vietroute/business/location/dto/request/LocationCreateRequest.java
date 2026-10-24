package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.validation.BusinessCode;

@Getter
@Setter
@Schema(description = "Tạo địa điểm thuộc tỉnh/thành đang hoạt động")
public class LocationCreateRequest extends LocationWriteRequest {

    @NotBlank(message = "{validation.required}")
    @BusinessCode
    @Schema(description = "Mã nội bộ bất biến, chữ hoa/số, 3–40 ký tự", example = "PLACE01")
    private String code;

    @NotNull(message = "{validation.required}")
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID tỉnh/thành đang hoạt động; giữ nguyên sau khi tạo", minimum = "1")
    private Long provinceId;
}
