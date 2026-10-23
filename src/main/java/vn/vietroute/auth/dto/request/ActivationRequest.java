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

/** Request auth không nhận user ID, role, audit hoặc trạng thái xác minh do client gửi. */
@Getter
@Setter
@Schema(description = "ActivationRequest cho vòng đời tài khoản")
public class ActivationRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email nhận mã")
    private String email;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.ACCOUNT_TOKEN, message = "{validation.invalid}")
    @Schema(description = "Mã ngẫu nhiên 43 ký tự trong email, không phải JWT")
    private String token;
}
