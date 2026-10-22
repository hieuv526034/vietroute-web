package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.common.base.BaseRequest;

/** Thay thế toàn bộ quyền của role với version; client không gửi actor/audit. */
@Getter
@Setter
@Schema(description = "Thay quyền chức năng của role; ADMIN được bảo vệ")
public class RolePermissionsRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Size(max = 32, message = "{validation.length}")
    @Schema(description = "Tập quyền mới; tập rỗng thu hồi mọi quyền chức năng")
    private Set<@NotNull Permission> permissions;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của cấu hình role", example = "0", minimum = "0")
    private long version;
}
