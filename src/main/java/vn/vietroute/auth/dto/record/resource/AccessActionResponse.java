package vn.vietroute.auth.dto.record.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Một quyền và các endpoint mà quyền đó tham gia bảo vệ")
public record AccessActionResponse(
    @Schema(description = "Mã quyền") String permission,
    @Schema(description = "Tên hành động theo locale") String name,
    @Schema(description = "Tên hành động VIEW/READ/CREATE...; WRITE là mã tương thích") String action,
    @Schema(description = "Endpoint thật; hiển thị cả điều kiện ANY/ALL và ADMIN nếu có")
    List<AccessEndpointResponse> endpoints
) {}
