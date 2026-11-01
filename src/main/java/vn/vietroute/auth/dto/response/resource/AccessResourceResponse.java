package vn.vietroute.auth.dto.response.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadata lấy từ mapping server; không nhận URL, public flag hay controller do client khai báo. */
@Schema(description = "Tài nguyên UI/API được ứng dụng tự đồng bộ, không tự cấp quyền")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessResourceResponse {

    @Schema(description = "MENU: trang giao diện; ENDPOINT: API hoặc endpoint kỹ thuật")
    private String kind;

    @Schema(description = "HTTP method và template path, không có origin hay payload")
    private String target;

    @Schema(description = "Tên controller và phương thức xử lý")
    private String handler;

    @Schema(description = "Mô tả từ Operation hoặc tên phương thức khi chưa có mô tả")
    private String description;

    @Schema(description = "Công khai theo WebRoutePolicy, chỉ đọc và không sửa từ DB")
    private boolean publicRoute;

    @Schema(description = "Có thể gắn ràng buộc quyền bổ sung; không bỏ guard nền")
    private boolean configurable;

    @Schema(description = "Lần đầu startup ghi nhận tài nguyên")
    private Instant firstSeenAt;

    @Schema(description = "Lần startup gần nhất quan sát; không phải trạng thái node đang online")
    private Instant lastSeenAt;

    @Schema(description = "Nhóm chức năng từ AccessFunction, GENERAL khi chưa khai báo")
    private String group;

    @Schema(description = "Mã quyền guard HTTP; match chỉ rõ ANY/ALL, mapping cấp cho role nằm trong DB")
    private List<String> declaredPermissions;

    @Schema(description = "Mã chức năng trong cây; rỗng với public/self-service")
    private String function;

    @Schema(description = "ANY/ALL của guard khai báo")
    private String match;

    @Schema(description = "Giới hạn bổ sung chỉ ADMIN cho dữ liệu nhạy cảm")
    private boolean adminOnly;

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
        List<String> declaredPermissions,
        String function,
        String match,
        boolean adminOnly
    ) {
        this.kind = kind;
        this.target = target;
        this.handler = handler;
        this.description = description;
        this.publicRoute = publicRoute;
        this.configurable = configurable;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.group = group;
        this.declaredPermissions = declaredPermissions;
        this.function = function;
        this.match = match;
        this.adminOnly = adminOnly;
    }

    public String kind() {
        return kind;
    }

    public String target() {
        return target;
    }

    public String handler() {
        return handler;
    }

    public String description() {
        return description;
    }

    public boolean publicRoute() {
        return publicRoute;
    }

    public boolean configurable() {
        return configurable;
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public String group() {
        return group;
    }

    public List<String> declaredPermissions() {
        return declaredPermissions;
    }

    public String function() {
        return function;
    }

    public String match() {
        return match;
    }

    public boolean adminOnly() {
        return adminOnly;
    }

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
