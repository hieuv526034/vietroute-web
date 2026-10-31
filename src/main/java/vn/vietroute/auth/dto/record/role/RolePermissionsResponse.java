package vn.vietroute.auth.dto.record.role;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Cấu hình quyền role hiện hành")
public record RolePermissionsResponse(
    @Schema(description = "Role") Role role,
    @Schema(description = "Các quyền chức năng") Set<Permission> permissions,
    @Schema(description = "Version chống ghi đè thay đổi đồng thời") long version,
    @Schema(description = "Thời điểm cập nhật UTC") Instant updatedAt,
    @Schema(description = "ID người cập nhật; 0 là system khi bootstrap") Long updatedBy,
    @Schema(description = "Số tài khoản đang gán role, gồm cả tài khoản tắt; snapshot lúc đọc") long assignedUsers,
    @Schema(description = "Role đang hoạt động; role tắt vẫn có thể sửa mapping trước khi bật lại") boolean active
) {
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
