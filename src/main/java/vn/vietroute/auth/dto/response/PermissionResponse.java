package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.auth.entity.Permission;

@Schema(description = "Quyền chức năng và tên theo locale")
public record PermissionResponse(
    @Schema(description = "Mã quyền ổn định") Permission code,
    @Schema(description = "Key dịch tên quyền") String messageKey,
    @Schema(description = "Tên quyền theo ngôn ngữ yêu cầu") String name
) {}
