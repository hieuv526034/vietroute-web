package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Function;

/** Function và projection resource/menu, dùng cùng tập endpoint DB cho UI; không có quyền cố định. */
@Getter
@Setter
@NoArgsConstructor
public class FunctionDTO {

    @Schema(description = "Mã Function")
    private String code;

    @Schema(description = "Mã Function trong capabilities")
    private String function;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Nhãn menu đã chọn locale")
    private String name;

    @Schema(description = "Mô tả")
    private String description;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "Có menu được cấp")
    private boolean view;

    @Schema(description = "Endpoint được gán")
    private Set<String> targets = Set.of();

    @Schema(description = "Version")
    private long version;

    @Schema(description = "Audit UTC")
    private Instant updatedAt;

    @Schema(description = "Actor server")
    private Long updatedBy;

    @Schema(description = "Chỉ resource: MENU hoặc ENDPOINT")
    private String kind;

    @Schema(description = "Chỉ resource: method + template")
    private String target;

    @Schema(description = "Chỉ resource: HTTP method")
    private String httpMethod;

    @Schema(description = "Chỉ resource: template server")
    private String pathTemplate;

    @Schema(description = "Chỉ resource: tên handler")
    private String handler;

    @Schema(description = "Chỉ resource: public theo source")
    private boolean publicEndpoint;

    @Schema(description = "Chỉ resource: tự phục vụ theo source")
    private boolean selfService;

    @Schema(description = "Menu: URL nội bộ")
    private String path;

    @Schema(description = "Menu: icon")
    private String icon = "shield";

    @Schema(description = "Thứ tự hiển thị chức năng/menu")
    private int sortOrder;

    @Schema(description = "Menu con, không tự kế thừa grant")
    private List<FunctionDTO> children = List.of();

    public static FunctionDTO from(Function value, Set<String> targets) {
        FunctionDTO dto = new FunctionDTO();
        dto.code = value.getCode();
        dto.function = value.getCode();
        dto.nameVi = value.getNameVi();
        dto.nameEn = value.getNameEn();
        dto.description = value.getDescription();
        dto.icon = value.getIcon();
        dto.sortOrder = value.getSortOrder();
        dto.active = value.isActive();
        dto.targets = targets;
        dto.version = value.getVersion();
        dto.updatedAt = value.getUpdatedAt();
        dto.updatedBy = value.getUpdatedBy();
        return dto;
    }

    public String path() {
        return path;
    }

    public String name() {
        return name;
    }

    public String icon() {
        return icon;
    }

    public boolean active() {
        return active;
    }

    public List<FunctionDTO> children() {
        return children;
    }
}
