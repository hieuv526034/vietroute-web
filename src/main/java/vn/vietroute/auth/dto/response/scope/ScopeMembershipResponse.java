package vn.vietroute.auth.dto.response.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Membership và role riêng tại scope; không chứa mật khẩu/email/token")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ScopeMembershipResponse {

    @Schema(description = "ID scope")
    private long scopeId;

    @Schema(description = "ID tài khoản được gán")
    private long userId;

    @Schema(description = "Tên đăng nhập join theo userId, chỉ khu quản trị")
    private String username;

    @Schema(description = "Membership bật; role/quyền/account/scope vẫn cần active khi thao tác")
    private boolean active;

    @Schema(description = "Các mapping role riêng trong scope, gồm role đang tắt để admin thu hồi")
    private Set<Role> roles;

    @Schema(description = "Version chống ghi đè")
    private long version;

    @Schema(description = "Thời điểm cập nhật UTC")
    private Instant updatedAt;

    @Schema(description = "Actor cập nhật")
    private long updatedBy;

    public ScopeMembershipResponse(
        long scopeId,
        long userId,
        String username,
        boolean active,
        Set<Role> roles,
        long version,
        Instant updatedAt,
        long updatedBy
    ) {
        this.scopeId = scopeId;
        this.userId = userId;
        this.username = username;
        this.active = active;
        this.roles = roles;
        this.version = version;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public long scopeId() {
        return scopeId;
    }

    public long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public boolean active() {
        return active;
    }

    public Set<Role> roles() {
        return roles;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long updatedBy() {
        return updatedBy;
    }
}
