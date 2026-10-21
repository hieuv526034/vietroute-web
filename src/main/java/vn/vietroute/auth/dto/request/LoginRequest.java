package vn.vietroute.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.Constants;

/** Mật khẩu chỉ được nhận vào, không serialize ngược hoặc thêm vào toString. */
@Getter
@Setter
@Schema(description = "Thông tin đăng nhập")
public class LoginRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Constants.USERNAME_PATTERN, message = "{validation.username}")
    @Schema(
        description = "Tên đăng nhập gồm chữ/số, 3–32 ký tự",
        example = "admin",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String username;

    @NotBlank(message = "{validation.required}")
    @Size(max = 72, message = "{validation.passwordLength}")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(
        description = "Mật khẩu; không ghi log",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String password;
}
