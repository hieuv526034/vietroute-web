package vn.vietroute.auth.dto.request;

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
@Schema(description = "EmailRequest cho vòng đời tài khoản")
public class EmailRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email tài khoản, được chuẩn hóa phía server")
    private String email;
}
