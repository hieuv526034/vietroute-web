package vn.vietroute.auth.dto.request.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Tra prefix username, giới hạn ký tự để UI không nhận wildcard tìm kiếm tùy ý. */
@Getter
@Setter
@Schema(description = "Tra cứu tài khoản để gán role; yêu cầu ACCESS_READ")
public class AccessUserSearchRequest extends PageRequest {

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = Regex.USERNAME_SEARCH, message = "{validation.usernameSearch}")
    @Schema(
        description = "Tiền tố username không phân biệt hoa/thường; rỗng để xem tất cả",
        maxLength = 32,
        defaultValue = ""
    )
    private String username = "";
}
