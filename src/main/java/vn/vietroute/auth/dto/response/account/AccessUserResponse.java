package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

/** DTO whitelist cho màn gán role, không có password/token/lockedUntil hoặc dữ liệu credential. */
@Schema(description = "Metadata tài khoản phục vụ gán role, không chứa credential")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessUserResponse {

    @Schema(description = "ID tài khoản")
    private long id;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Tên hiển thị")
    private String displayName;

    @Schema(description = "Trạng thái hoạt động")
    private boolean active;

    @Schema(description = "Các role hiện hành")
    private Set<Role> roles;

    @Schema(description = "Version dùng khi gán role")
    private long version;

    @Schema(description = "Thời điểm tạo UTC")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật UTC")
    private Instant updatedAt;

    public AccessUserResponse(
        long id,
        String username,
        String displayName,
        boolean active,
        Set<Role> roles,
        long version,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.active = active;
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
}
