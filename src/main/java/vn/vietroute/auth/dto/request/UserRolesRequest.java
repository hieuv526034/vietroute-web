package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.BaseRequest;

@Getter
@Setter
@Schema(description = "Gán role cho tài khoản với optimistic version; không tự sửa role của chính mình")
public class UserRolesRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Size(min = 1, max = 32, message = "{validation.length}")
    @Schema(description = "Danh sách role mới")
    private Set<@NotNull Role> roles;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của user", minimum = "0")
    @NotNull(message = "{validation.required}")
    private Long version;
}
