package vn.vietroute.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Payload theo tài nguyên; điều kiện bắt buộc riêng của từng endpoint được kiểm ở service và mô tả trong Operation. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload UserRequest; không nhận actor/audit hoặc hash")
public class UserRequest {

    @Schema(description = "Username bắt buộc khi tạo/đăng nhập; không nhận email làm tên đăng nhập")
    @Pattern(regexp = Regex.USERNAME, message = "{validation.username}")
    private String username;

    @Schema(description = "Tên hiển thị")
    @Size(max = 120, message = "{validation.length}")
    private String displayName;

    @Schema(description = "Email thông tin tài khoản do quản trị cấp; không dùng đăng nhập")
    @Email(message = "{validation.email}")
    @Size(max = 254)
    private String email;

    @Schema(description = "Mật khẩu đăng nhập hoặc khởi tạo; không trả lại")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 72)
    private String password;

    @Schema(description = "Mật khẩu hiện tại khi đổi mật khẩu")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 72)
    private String currentPassword;

    @Schema(description = "Mật khẩu mới khi đổi mật khẩu")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 72)
    private String newPassword;

    @Schema(description = "Mã role; backend kiểm tồn tại và trần ủy quyền")
    @Size(max = 32)
    private Set<String> roles;

    @Schema(description = "Trạng thái; tạo mới mặc định hoạt động")
    private Boolean active;

    @Schema(description = "Version bắt buộc khi sửa/gán")
    @Min(0)
    private Long version;

    @Schema(description = "0 là role toàn cục, lớn hơn 0 là phạm vi nhà xe")
    @Min(0)
    private Long scopeId;
}
