package vn.vietroute.auth.dto.response.role;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Cấu hình quyền role hiện hành")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class RolePermissionsResponse {

    @Schema(description = "Role")
    private Role role;

    @Schema(description = "Các quyền chức năng")
    private Set<Permission> permissions;

    @Schema(description = "Version chống ghi đè thay đổi đồng thời")
    private long version;

    @Schema(description = "Thời điểm cập nhật UTC")
    private Instant updatedAt;

    @Schema(description = "ID người cập nhật; 0 là system khi bootstrap")
    private Long updatedBy;

    @Schema(description = "Số tài khoản đang gán role, gồm cả tài khoản tắt; snapshot lúc đọc")
    private long assignedUsers;

    @Schema(description = "Role đang hoạt động; role tắt vẫn có thể sửa mapping trước khi bật lại")
    private boolean active;

    public RolePermissionsResponse(
        Role role,
        Set<Permission> permissions,
        long version,
        Instant updatedAt,
        Long updatedBy,
        long assignedUsers,
        boolean active
    ) {
        this.role = role;
        this.permissions = permissions;
        this.version = version;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.assignedUsers = assignedUsers;
        this.active = active;
    }

    public Role role() {
        return role;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long updatedBy() {
        return updatedBy;
    }

    public long assignedUsers() {
        return assignedUsers;
    }

    public boolean active() {
        return active;
    }

    public RolePermissionsResponse(
        Role role,
        Set<Permission> permissions,
        long version,
        Instant updatedAt,
        Long updatedBy
    ) {
        this(role, permissions, version, updatedAt, updatedBy, 0, true);
    }
}
