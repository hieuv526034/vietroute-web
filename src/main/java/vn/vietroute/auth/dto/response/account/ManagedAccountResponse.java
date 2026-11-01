package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;

@Schema(description = "Thông tin quản trị danh tính, không trả password/hash/token/email")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ManagedAccountResponse {

    @Schema(description = "ID tài khoản")
    private long id;

    @Schema(description = "Tên đăng nhập")
    private String username;

    @Schema(description = "Tên hiển thị")
    private String displayName;

    @Schema(description = "Tài khoản đang hoạt động")
    private boolean active;

    @Schema(description = "Chờ xác minh email trước khi kích hoạt")
    private boolean activationPending;

    @Schema(description = "Cần đổi mật khẩu tạm trước khi sử dụng chức năng")
    private boolean mustChangePassword;

    @Schema(description = "Chưa xác minh email nên chưa có quyền chức năng riêng tư; không công bố địa chỉ email")
    private boolean emailVerificationRequired;

    @Schema(description = "Hết khóa tạm do đăng nhập sai, UTC; null khi không khóa")
    private Instant lockedUntil;

    @Schema(description = "Mã role đã gán, gồm cả role đang tắt")
    private Set<Role> roles;

    @Schema(description = "Version cho cập nhật trạng thái hoặc role", minimum = "0")
    private long version;

    @Schema(description = "Ngày tạo UTC")
    private Instant createdAt;

    @Schema(description = "Ngày cập nhật UTC")
    private Instant updatedAt;

    public ManagedAccountResponse(
        long id,
        String username,
        String displayName,
        boolean active,
        boolean activationPending,
        boolean mustChangePassword,
        boolean emailVerificationRequired,
        Instant lockedUntil,
        Set<Role> roles,
        long version,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.active = active;
        this.activationPending = activationPending;
        this.mustChangePassword = mustChangePassword;
        this.emailVerificationRequired = emailVerificationRequired;
        this.lockedUntil = lockedUntil;
        this.roles = roles;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public boolean active() {
        return active;
    }

    public boolean activationPending() {
        return activationPending;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public boolean emailVerificationRequired() {
        return emailVerificationRequired;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Set<Role> roles() {
        return roles;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

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
