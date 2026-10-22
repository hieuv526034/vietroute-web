package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;

@Schema(description = "Quyền chức năng và tên theo locale")
public record PermissionResponse(
    @Schema(description = "Mã quyền ổn định") Permission code,
    @Schema(description = "Nhóm tài nguyên, ví dụ CATALOG/ACCESS/JOB") String resource,
    @Schema(description = "Hành động được cho phép; WRITE tương thích chỉ gồm CREATE/UPDATE")
    Set<PermissionAction> actions,
    @Schema(description = "Key dịch tên quyền") String messageKey,
    @Schema(description = "Tên quyền theo ngôn ngữ yêu cầu") String name
) {}
