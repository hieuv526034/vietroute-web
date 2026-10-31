package vn.vietroute.auth.dto.record.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;

@Schema(description = "Thông tin quản trị danh tính, không trả password/hash/token/email")
public record ManagedAccountResponse(
    @Schema(description = "ID tài khoản") long id,
    @Schema(description = "Tên đăng nhập") String username,
    @Schema(description = "Tên hiển thị") String displayName,
    @Schema(description = "Tài khoản đang hoạt động") boolean active,
    @Schema(description = "Chờ xác minh email trước khi kích hoạt") boolean activationPending,
    @Schema(description = "Cần đổi mật khẩu tạm trước khi sử dụng chức năng") boolean mustChangePassword,
    @Schema(description = "Chưa xác minh email nên chưa có quyền chức năng riêng tư; không công bố địa chỉ email")
    boolean emailVerificationRequired,
    @Schema(description = "Hết khóa tạm do đăng nhập sai, UTC; null khi không khóa") Instant lockedUntil,
    @Schema(description = "Mã role đã gán, gồm cả role đang tắt") Set<Role> roles,
    @Schema(description = "Version cho cập nhật trạng thái hoặc role", minimum = "0") long version,
    @Schema(description = "Ngày tạo UTC") Instant createdAt,
    @Schema(description = "Ngày cập nhật UTC") Instant updatedAt
) {
    public static ManagedAccountResponse from(UserAccount user, Set<Role> assignedRoles) {
        return new ManagedAccountResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.isActive(),
            user.isActivationPending(),
            user.isMustChangePassword(),
            user.getEmailVerifiedAt() == null,
            user.getLockedUntil(),
            Set.copyOf(assignedRoles),
            user.getVersion(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
