package vn.vietroute.auth.dto.response.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Permission;

@Schema(
    description = "Quyền hiện hành của chính user tại scope; UI kết hợp capabilities/policy chung trước khi hiện nút"
)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ScopePermissionsResponse {

    @Schema(description = "ID scope đã kiểm trong DB")
    private long scopeId;

    @Schema(description = "Permission từ role riêng tại scope; ADMIN nhận quyền toàn cục hiện hành, không token/cache")
    private Set<Permission> permissions;

    public ScopePermissionsResponse(long scopeId, Set<Permission> permissions) {
        this.scopeId = scopeId;
        this.permissions = permissions;
    }

    public long scopeId() {
        return scopeId;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
