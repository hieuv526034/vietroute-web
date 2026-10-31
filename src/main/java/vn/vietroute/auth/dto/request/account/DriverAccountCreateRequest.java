package vn.vietroute.auth.dto.request.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.StrongPassword;

/** Credentials thuộc auth; không nhận role/scope/trạng thái xác minh hoặc hash từ client. */
@Getter
@Setter
@Schema(description = "Cấp DRIVER mới cho hồ sơ chưa liên kết, bắt đổi mật khẩu và xác minh email")
public class DriverAccountCreateRequest {

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.USERNAME, message = "{validation.username}")
    @Schema(description = "Username 3–32 chữ/số, chuẩn hóa chữ thường")
    private String username;

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.invalid}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Email chưa xác minh; không tự gửi thư hoặc cấp ngoại lệ")
    private String email;

    @NotBlank(message = "{validation.required}")
    @StrongPassword
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Mật khẩu tạm mạnh, tối đa 72 byte UTF-8; chỉ nhận, không trả hoặc lưu browser")
    private String password;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hồ sơ tài xế chống tạo/gán đồng thời")
    private long version;
}
