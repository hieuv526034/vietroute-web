package vn.vietroute.auth.dto.request.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

/** Request auth không nhận user ID, role, audit hoặc trạng thái xác minh do client gửi. */
@Getter
@Setter
@Schema(description = "BindEmailRequest cho vòng đời tài khoản")
public class BindEmailRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email muốn xác minh cho tài khoản đang đăng nhập")
    private String email;

    @NotBlank(message = "{validation.required}")
    @Size(max = 72, message = "{validation.length}")
    @Schema(description = "Mật khẩu hiện tại để xác nhận chủ tài khoản")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String currentPassword;
}
