package vn.vietroute.auth.dto.response.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Nút cây chức năng từ định nghĩa server, quyền role lấy từ DB")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessFeatureResponse {

    @Schema(description = "Mã chức năng ổn định")
    private String code;

    @Schema(description = "Mã nhóm cha; null là nút gốc")
    private String parent;

    @Schema(description = "Tên theo locale")
    private String name;

    @Schema(description = "URL trang trong ứng dụng; null khi chỉ có API/nhóm")
    private String path;

    @Schema(description = "Quyền vào trang; null cho nhóm/API thuần")
    private String viewPermission;

    @Schema(description = "Các hành động và endpoint đang đăng ký trên node")
    private List<AccessActionResponse> actions;

    @Schema(description = "Thuộc mẫu gán nhanh bộ quyền hệ thống; không tự cấp quyền, không bỏ kiểm ADMIN/scope")
    private boolean systemBundle;

    public AccessFeatureResponse(
        String code,
        String parent,
        String name,
        String path,
        String viewPermission,
        List<AccessActionResponse> actions,
        boolean systemBundle
    ) {
        this.code = code;
        this.parent = parent;
        this.name = name;
        this.path = path;
        this.viewPermission = viewPermission;
        this.actions = actions;
        this.systemBundle = systemBundle;
    }

    public String code() {
        return code;
    }

    public String parent() {
        return parent;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public String viewPermission() {
        return viewPermission;
    }

    public List<AccessActionResponse> actions() {
        return actions;
    }

    public boolean systemBundle() {
        return systemBundle;
    }
}
