package vn.vietroute.auth.dto.response.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;

@Schema(description = "Quyền chức năng và tên theo locale")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class PermissionResponse {

    @Schema(description = "Mã quyền ổn định")
    private Permission code;

    @Schema(description = "Nhóm tài nguyên, ví dụ CATALOG/ACCESS/JOB")
    private String resource;

    @Schema(description = "Hành động được cho phép; WRITE tương thích theo từng resource")
    private Set<PermissionAction> actions;

    @Schema(description = "Key dịch tên quyền")
    private String messageKey;

    @Schema(description = "Tên quyền theo ngôn ngữ yêu cầu")
    private String name;

    public PermissionResponse(
        Permission code,
        String resource,
        Set<PermissionAction> actions,
        String messageKey,
        String name
    ) {
        this.code = code;
        this.resource = resource;
        this.actions = actions;
        this.messageKey = messageKey;
        this.name = name;
    }

    public Permission code() {
        return code;
    }

    public String resource() {
        return resource;
    }

    public Set<PermissionAction> actions() {
        return actions;
    }

    public String messageKey() {
        return messageKey;
    }

    public String name() {
        return name;
    }
}
