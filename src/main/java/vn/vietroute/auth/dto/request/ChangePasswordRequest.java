package vn.vietroute.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.validation.StrongPassword;

/** Đổi mật khẩu yêu cầu xác minh mật khẩu hiện tại; không nhận user ID từ client. */
@Getter @Setter @Schema(description = "Đổi mật khẩu tài khoản hiện tại và thu hồi các token cũ")
public class ChangePasswordRequest extends BaseRequest {
    @NotBlank(message = "{validation.required}") @Size(max = 72, message = "{validation.passwordLength}")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Mật khẩu hiện tại", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String currentPassword;
    @StrongPassword
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Mật khẩu mới: ít nhất 8 ký tự, có hoa/thường/số/ký tự đặc biệt, tối đa 72 byte UTF-8",
            format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
