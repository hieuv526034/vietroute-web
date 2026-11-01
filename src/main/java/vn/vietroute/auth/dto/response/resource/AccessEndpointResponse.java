package vn.vietroute.auth.dto.response.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Binding endpoint chỉ đọc từ code, không cho client bỏ guard")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessEndpointResponse {

    @Schema(description = "HTTP method và template path")
    private String target;

    @Schema(description = "Mô tả handler")
    private String description;

    @Schema(description = "Tập quyền liên quan")
    private List<String> permissions;

    @Schema(description = "ANY hoặc ALL")
    private String match;

    @Schema(description = "Chỉ ADMIN ngoài các quyền hành động")
    private boolean adminOnly;

    public AccessEndpointResponse(
        String target,
        String description,
        List<String> permissions,
        String match,
        boolean adminOnly
    ) {
        this.target = target;
        this.description = description;
        this.permissions = permissions;
        this.match = match;
        this.adminOnly = adminOnly;
    }

    public String target() {
        return target;
    }

    public String description() {
        return description;
    }

    public List<String> permissions() {
        return permissions;
    }

    public String match() {
        return match;
    }

    public boolean adminOnly() {
        return adminOnly;
    }
}
