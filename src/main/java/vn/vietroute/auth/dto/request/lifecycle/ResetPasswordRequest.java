package vn.vietroute.auth.dto.request.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.StrongPassword;

/** Request auth không nhận user ID, role, audit hoặc trạng thái xác minh do client gửi. */
@Getter
@Setter
@Schema(description = "ResetPasswordRequest cho vòng đời tài khoản")
public class ResetPasswordRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email đã xác minh")
    private String email;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.ACCOUNT_TOKEN, message = "{validation.invalid}")
    @Schema(description = "Mã đặt lại một lần, hết hạn sau 15 phút")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token;

    @NotBlank(message = "{validation.required}")
    @StrongPassword
    @Schema(description = "Mật khẩu mới không trùng hiện tại và hai mật khẩu trước")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String newPassword;
}
