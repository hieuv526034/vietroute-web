package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.validation.BusinessCode;

/** Code/nhà xe được chọn khi tạo, sau đó giữ bất biến; scope vẫn suy ra từ DB. */
@Getter
@Setter
@Schema(description = "Tạo Carrier")
public class CarrierCreateRequest extends CarrierWriteRequest {

    @NotBlank(message = "{validation.required}")
    @BusinessCode
    @Schema(description = "Mã nghiệp vụ chữ hoa/số 3–40 ký tự, bắt đầu bằng chữ")
    private String code;
}
