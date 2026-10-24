package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import vn.vietroute.auth.entity.Permission;

@Schema(
    description = "Quyền hiện hành của chính user tại scope; UI kết hợp capabilities/policy chung trước khi hiện nút"
)
public record ScopePermissionsResponse(
    @Schema(description = "ID scope đã kiểm trong DB") long scopeId,
    @Schema(description = "Permission từ role riêng tại scope; ADMIN nhận quyền toàn cục hiện hành, không token/cache")
    Set<Permission> permissions
) {}
