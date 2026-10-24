package vn.vietroute.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.StrongPassword;

/** Admin cấp tài khoản và role trong một transaction; không nhận audit/tokenVersion hoặc hash. */
@Getter
@Setter
@Schema(description = "Cấp tài khoản hoạt động; bắt đổi mật khẩu tạm sau lần đăng nhập đầu")
public class ManagedAccountCreateRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.USERNAME, message = "{validation.username}")
    @Schema(description = "Tên đăng nhập chữ/số 3–32 ký tự, chuẩn hóa chữ thường", example = "taixe01")
    private String username;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên hiển thị tối đa 120 ký tự", example = "Nguyễn Văn An")
    private String displayName;

    @NotBlank(message = "{validation.required}")
    @StrongPassword
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Mật khẩu tạm mạnh, tối đa 72 byte UTF-8; không trả lại trong response")
    private String password;

    @NotNull(message = "{validation.required}")
    @Size(min = 1, max = 32, message = "{validation.length}")
    @Schema(description = "Các role đang bật trong registry DB; không tự sinh quyền hoặc membership nhà xe")
    private Set<@NotNull Role> roles;
}
