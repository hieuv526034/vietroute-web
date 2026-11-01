package vn.vietroute.auth.dto.response.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Một quyền và các endpoint mà quyền đó tham gia bảo vệ")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessActionResponse {

    @Schema(description = "Mã quyền")
    private String permission;

    @Schema(description = "Tên hành động theo locale")
    private String name;

    @Schema(description = "Tên hành động VIEW/READ/CREATE...; WRITE là mã tương thích")
    private String action;

    @Schema(description = "Endpoint thật; hiển thị cả điều kiện ANY/ALL và ADMIN nếu có")
    private List<AccessEndpointResponse> endpoints;

    public AccessActionResponse(String permission, String name, String action, List<AccessEndpointResponse> endpoints) {
        this.permission = permission;
        this.name = name;
        this.action = action;
        this.endpoints = endpoints;
    }

    public String permission() {
        return permission;
    }

    public String name() {
        return name;
    }

    public String action() {
        return action;
    }

    public List<AccessEndpointResponse> endpoints() {
        return endpoints;
    }
}
