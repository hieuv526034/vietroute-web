package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.common.base.BaseResponse;

/** Mapper whitelist trường công khai, không serialize trực tiếp entity tài khoản. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Thông tin tài khoản hiện tại, không chứa hash hoặc tokenVersion")
public class UserResponse extends BaseResponse {

    @Schema(description = "Tên đăng nhập", example = "admin")
    private String username;

    @Schema(description = "Tên hiển thị", example = "Quản trị viên")
    private String displayName;

    @Schema(description = "Tài khoản đang hoạt động")
    private boolean active;

    @Schema(description = "Cần đổi mật khẩu khởi tạo")
    private boolean mustChangePassword;

    @Schema(description = "Các vai trò hệ thống")
    private Set<Role> roles;

    public static UserResponse from(UserAccount user) {
        return UserResponse.builder()
            .id(user.getId())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .createdBy(user.getCreatedBy())
            .updatedBy(user.getUpdatedBy())
            .version(user.getVersion())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .active(user.isActive())
            .mustChangePassword(user.isMustChangePassword())
            .roles(Set.copyOf(user.getRoles()))
            .build();
    }
}
