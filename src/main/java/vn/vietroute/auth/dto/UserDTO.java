package vn.vietroute.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.User;

/** Projection whitelist tài khoản; credential/hash/tokenVersion không ra HTTP. Login dùng thêm token/user. */
@Getter
@Setter
@NoArgsConstructor
public class UserDTO {

    @Schema(description = "ID tài khoản")
    private Long id;

    @Schema(description = "Tên đăng nhập")
    private String username;

    @Schema(description = "Tên hiển thị")
    private String displayName;

    @Schema(description = "Email tài khoản")
    private String email;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "Cần đổi mật khẩu khởi tạo")
    private boolean mustChangePassword;

    @Schema(description = "Không còn luồng kích hoạt email tự phục vụ")
    private boolean activationPending;

    @Schema(description = "Cần người quản trị xác minh tài khoản")
    private boolean emailVerificationRequired;

    @Schema(description = "Thời điểm xác minh email")
    private Instant emailVerifiedAt;

    @Schema(description = "Role hiện hành")
    private Set<String> roles = Set.of();

    @Schema(description = "Endpoint được gán trong DB")
    private Set<String> targets = Set.of();

    @Schema(description = "Version cập nhật")
    private long version;

    @Schema(description = "ID ảnh đại diện")
    private Long avatarMediaId;

    @Schema(description = "URL ảnh READY")
    private String avatarUrl;

    @Schema(description = "Audit UTC")
    private Instant updatedAt;

    @Schema(description = "Actor server cập nhật")
    private Long updatedBy;

    @Schema(description = "Chỉ login: JWT; browser dùng cookie HttpOnly")
    private String accessToken;

    @Schema(description = "Chỉ login: kiểu token")
    private String tokenType;

    @Schema(description = "Chỉ login: hết hạn UTC")
    private Instant expiresAt;

    @Schema(description = "Chỉ login/preview: thông tin tài khoản")
    private UserDTO user;

    @Schema(description = "Chỉ preview: chức năng/endpoint của tài khoản đích")
    private List<FunctionDTO> capabilities = List.of();

    public static UserDTO from(User value) {
        UserDTO dto = new UserDTO();
        dto.id = value.getId();
        dto.username = value.getUsername();
        dto.displayName = value.getDisplayName();
        dto.email = value.getEmail();
        dto.active = value.isActive();
        dto.mustChangePassword = value.isMustChangePassword();
        dto.activationPending = value.isActivationPending();
        dto.emailVerifiedAt = value.getEmailVerifiedAt();
        dto.emailVerificationRequired = value.getEmailVerifiedAt() == null;
        dto.roles = value.getRoles();
        dto.targets = value.getTargets();
        dto.version = value.getVersion();
        dto.avatarMediaId = value.getAvatarMediaId();
        dto.avatarUrl = value.getAvatarUrl();
        dto.updatedAt = value.getUpdatedAt();
        dto.updatedBy = value.getUpdatedBy();
        return dto;
    }
}
