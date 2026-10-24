package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

@Getter
@Setter
@Schema(description = "Tra membership theo phạm vi và ID tài khoản, phân trang DB")
public class ScopeMemberSearchRequest extends PageRequest {

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Chỉ xem một tài khoản nếu có, bỏ trống lấy cả phạm vi")
    private Long userId;
}
