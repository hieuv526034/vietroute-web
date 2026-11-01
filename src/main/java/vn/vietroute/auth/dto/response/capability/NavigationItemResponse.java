package vn.vietroute.auth.dto.response.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** View model cây menu; chỉ giữ các nhánh có trang được phép mở, không thay guard URL. */
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class NavigationItemResponse {

    @Schema(description = "Tên menu theo locale")
    private String name;

    @Schema(description = "Path trong ứng dụng; null với nhóm")
    private String path;

    @Schema(description = "Icon SVG nội bộ")
    private String icon;

    @Schema(description = "Trang/nhánh đang được mở")
    private boolean active;

    @Schema(description = "Menu con đã lọc bằng capabilities")
    private List<NavigationItemResponse> children;

    public NavigationItemResponse(
        String name,
        String path,
        String icon,
        boolean active,
        List<NavigationItemResponse> children
    ) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.active = active;
        this.children = children;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public String icon() {
        return icon;
    }

    public boolean active() {
        return active;
    }

    public List<NavigationItemResponse> children() {
        return children;
    }
}
