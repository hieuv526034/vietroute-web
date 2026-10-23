package vn.vietroute.auth.dto.request;

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
@Schema(description = "RegisterRequest cho vòng đời tài khoản")
public class RegisterRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.USERNAME, message = "{validation.username}")
    @Schema(description = "Tên đăng nhập chỉ chữ/số, 3–32 ký tự")
    private String username;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên hiển thị tối đa 120 ký tự")
    private String displayName;

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email nhận mã kích hoạt")
    private String email;

    @NotBlank(message = "{validation.required}")
    @StrongPassword
    @Schema(description = "Mật khẩu mạnh, tối đa 72 byte UTF-8")
    private String password;
}
