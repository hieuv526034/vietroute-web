package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.dto.response.capability.AccessCapabilityResponse;

@Schema(description = "Menu/thao tác thực tế của tài khoản theo role active, guard API và policy; không giả lập JWT")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ManagedAccountAccessResponse {

    @Schema(description = "Tài khoản được kiểm tra")
    private ManagedAccountResponse user;

    @Schema(description = "Capabilities hiện tại; rỗng quyền nếu tắt/chờ kích hoạt/cần đổi mật khẩu")
    private List<AccessCapabilityResponse> capabilities;

    public ManagedAccountAccessResponse(ManagedAccountResponse user, List<AccessCapabilityResponse> capabilities) {
        this.user = user;
        this.capabilities = capabilities;
    }

    public ManagedAccountResponse user() {
        return user;
    }

    public List<AccessCapabilityResponse> capabilities() {
        return capabilities;
    }
}
