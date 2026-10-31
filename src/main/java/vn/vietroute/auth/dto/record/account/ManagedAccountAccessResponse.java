package vn.vietroute.auth.dto.record.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.vietroute.auth.dto.record.capability.AccessCapabilityResponse;

@Schema(description = "Menu/thao tác thực tế của tài khoản theo role active, guard API và policy; không giả lập JWT")
public record ManagedAccountAccessResponse(
    @Schema(description = "Tài khoản được kiểm tra") ManagedAccountResponse user,
    @Schema(description = "Capabilities hiện tại; rỗng quyền nếu tắt/chờ kích hoạt/cần đổi mật khẩu")
    List<AccessCapabilityResponse> capabilities
) {}
