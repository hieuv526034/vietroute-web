package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Nút cây chức năng từ định nghĩa server, quyền role lấy từ DB")
public record AccessFeatureResponse(
    @Schema(description = "Mã chức năng ổn định") String code,
    @Schema(description = "Mã nhóm cha; null là nút gốc") String parent,
    @Schema(description = "Tên theo locale") String name,
    @Schema(description = "URL trang trong ứng dụng; null khi chỉ có API/nhóm") String path,
    @Schema(description = "Quyền vào trang; null cho nhóm/API thuần") String viewPermission,
    @Schema(description = "Các hành động và endpoint đang đăng ký trên node") List<AccessActionResponse> actions,
    @Schema(description = "Thuộc mẫu gán nhanh bộ quyền hệ thống; không tự cấp quyền, không bỏ kiểm ADMIN/scope")
    boolean systemBundle
) {}
