package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Metadata lấy từ mapping server; không nhận URL, public flag hay controller do client khai báo. */
@Schema(description = "Tài nguyên UI/API được ứng dụng tự đồng bộ, không tự cấp quyền")
public record AccessResourceResponse(
    @Schema(description = "MENU: trang giao diện; ENDPOINT: API hoặc endpoint kỹ thuật") String kind,
    @Schema(description = "HTTP method và template path, không có origin hay payload") String target,
    @Schema(description = "Tên controller và phương thức xử lý") String handler,
    @Schema(description = "Mô tả từ Operation hoặc tên phương thức khi chưa có mô tả") String description,
    @Schema(description = "Công khai theo WebRoutePolicy, chỉ đọc và không sửa từ DB") boolean publicRoute,
    @Schema(description = "Có thể gắn ràng buộc quyền bổ sung; không bỏ guard nền") boolean configurable,
    @Schema(description = "Lần đầu startup ghi nhận tài nguyên") Instant firstSeenAt,
    @Schema(description = "Lần startup gần nhất quan sát; không phải trạng thái node đang online") Instant lastSeenAt,
    @Schema(description = "Nhóm chức năng từ AccessFunction, GENERAL khi chưa khai báo") String group,
    @Schema(description = "Mã quyền guard HTTP; match chỉ rõ ANY/ALL, mapping cấp cho role nằm trong DB")
    List<String> declaredPermissions,
    @Schema(description = "Mã chức năng trong cây; rỗng với public/self-service") String function,
    @Schema(description = "ANY/ALL của guard khai báo") String match,
    @Schema(description = "Giới hạn bổ sung chỉ ADMIN cho dữ liệu nhạy cảm") boolean adminOnly
) {
    public AccessResourceResponse(
        String kind,
        String target,
        String handler,
        String description,
        boolean publicRoute,
        boolean configurable,
        Instant firstSeenAt,
        Instant lastSeenAt,
        String group,
        List<String> declaredPermissions
    ) {
        this(
            kind,
            target,
            handler,
            description,
            publicRoute,
            configurable,
            firstSeenAt,
            lastSeenAt,
            group,
            declaredPermissions,
            "",
            "ANY",
            false
        );
    }
}
