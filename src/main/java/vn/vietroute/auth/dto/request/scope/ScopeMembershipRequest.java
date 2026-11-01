package vn.vietroute.auth.dto.request.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Gán role riêng trong phạm vi; role phải đang được gán toàn cục cho đúng tài khoản, không ADMIN")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ScopeMembershipRequest {

    @NotNull(message = "{validation.required}")
    @Size(max = 32, message = "{validation.invalid}")
    @Schema(description = "Role có hiệu lực tại phạm vi này; không tự nhận role ở phạm vi khác")
    private Set<@NotNull(message = "{validation.required}") Role> roles;

    @Schema(description = "Bật/tắt membership; tắt không xóa lịch sử")
    private boolean active;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "0 để tạo mới, cập nhật dùng version hiện hành >=1")
    private long version;

    public ScopeMembershipRequest(
        Set<@NotNull(message = "{validation.required}") Role> roles,
        boolean active,
        long version
    ) {
        this.roles = roles;
        this.active = active;
        this.version = version;
    }

    public Set<@NotNull(message = "{validation.required}") Role> roles() {
        return roles;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
