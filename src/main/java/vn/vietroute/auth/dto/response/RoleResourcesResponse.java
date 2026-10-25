package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "Tập menu/API được cấp cho một role")
public record RoleResourcesResponse(
    @Schema(description = "Mã role") String role,
    @Schema(description = "Target được cấp chính xác, không bao gồm public API") Set<String> targets,
    @Schema(description = "Version mapping, dùng chống ghi đè") long version
) {}
