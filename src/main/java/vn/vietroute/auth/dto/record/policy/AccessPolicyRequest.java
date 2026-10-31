package vn.vietroute.auth.dto.record.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

@Schema(description = "Chính sách siết thêm quyền của menu/endpoint có trong inventory")
public record AccessPolicyRequest(
    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.POLICY_KIND, message = "{validation.invalid}")
    @Schema(description = "Loại chính sách MENU hoặc ENDPOINT")
    String kind,
    @NotBlank(message = "{validation.required}")
    @Size(max = 240, message = "{validation.invalid}")
    @Schema(description = "Target từ inventory, ví dụ GET /monitoring")
    String target,
    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.invalid}")
    @Schema(description = "Tên quản trị tiếng Việt")
    String nameVi,
    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.invalid}")
    @Schema(description = "Tên quản trị tiếng Anh")
    String nameEn,
    @Schema(description = "true cho phép nếu đủ quyền, false khóa truy cập với người dùng thường") boolean enabled,
    @NotNull(message = "{validation.required}")
    @Size(max = 64, message = "{validation.invalid}")
    @Schema(description = "Phải có tất cả các quyền này")
    Set<@NotNull(message = "{validation.required}") Permission> permissions,
    @NotNull(message = "{validation.required}")
    @Size(max = 32, message = "{validation.invalid}")
    @Schema(description = "Rỗng không hạn chế role, khác rỗng cần ít nhất một role")
    Set<@NotNull(message = "{validation.required}") Role> roles,
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version khi sửa; tạo mới dùng 0")
    long version
) {}
