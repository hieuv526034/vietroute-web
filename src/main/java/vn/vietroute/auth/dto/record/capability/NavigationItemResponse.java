package vn.vietroute.auth.dto.record.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** View model cây menu; chỉ giữ các nhánh có trang được phép mở, không thay guard URL. */
public record NavigationItemResponse(
    @Schema(description = "Tên menu theo locale") String name,
    @Schema(description = "Path trong ứng dụng; null với nhóm") String path,
    @Schema(description = "Icon SVG nội bộ") String icon,
    @Schema(description = "Trang/nhánh đang được mở") boolean active,
    @Schema(description = "Menu con đã lọc bằng capabilities") List<NavigationItemResponse> children
) {}
