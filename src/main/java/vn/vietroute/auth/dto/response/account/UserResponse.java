package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.auth.entity.Permission;
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

    @Schema(description = "Email của tài khoản hiện tại; null nếu chưa gắn")
    private String email;

    @Schema(description = "ID ảnh đại diện đang gắn, null nếu chưa có")
    private Long avatarMediaId;

    @Schema(description = "URL thumbnail công khai từ ảnh READY, null nếu chưa có")
    private String avatarUrl;

    @Schema(description = "Thời điểm xác minh email; null khi chưa xác minh")
    private Instant emailVerifiedAt;

    @Schema(description = "Tài khoản đang hoạt động")
    private boolean active;

    @Schema(description = "Cần đổi mật khẩu khởi tạo")
    private boolean mustChangePassword;

    @Schema(
        description = "Phải gắn và xác minh email trước khi sử dụng chức năng riêng tư, không được miễn theo role ADMIN"
    )
    private boolean emailVerificationRequired;

    @Schema(description = "Các vai trò hệ thống")
    private Set<Role> roles;

    @Schema(description = "Quyền chức năng hiện tại từ database")
    private Set<Permission> permissions;

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
            .email(user.getEmail())
            .avatarMediaId(user.getAvatarMediaId())
            .avatarUrl(user.getAvatarUrl())
            .emailVerifiedAt(user.getEmailVerifiedAt())
            .active(user.isActive())
            .mustChangePassword(user.isMustChangePassword())
            .emailVerificationRequired(user.getEmailVerifiedAt() == null)
            .roles(Set.copyOf(user.getRoles()))
            .permissions(Set.copyOf(user.getPermissions()))
            .build();
    }
}
