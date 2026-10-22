package vn.vietroute.auth.dto.response;

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
    @Schema(description = "Người cập nhật, null nếu bootstrap") Long updatedBy
) {}
