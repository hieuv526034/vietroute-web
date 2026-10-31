package vn.vietroute.auth.dto.record.recovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Không nhận username/role/permission từ client; chỉ xác minh lại chính chủ tài khoản bootstrap. */
@Schema(description = "Xác nhận khôi phục quyền của chính tài khoản bootstrap; không đặt lại mật khẩu")
public record AdminRecoveryRequest(
    @NotBlank(message = "{validation.required}")
    @Size(max = 72, message = "{validation.passwordLength}")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(
        description = "Mật khẩu hiện tại của chính chủ; tối đa 72 byte UTF-8",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String currentPassword
) {}
