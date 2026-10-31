package vn.vietroute.auth.dto.record.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Binding endpoint chỉ đọc từ code, không cho client bỏ guard")
public record AccessEndpointResponse(
    @Schema(description = "HTTP method và template path") String target,
    @Schema(description = "Mô tả handler") String description,
    @Schema(description = "Tập quyền liên quan") List<String> permissions,
    @Schema(description = "ANY hoặc ALL") String match,
    @Schema(description = "Chỉ ADMIN ngoài các quyền hành động") boolean adminOnly
) {}
