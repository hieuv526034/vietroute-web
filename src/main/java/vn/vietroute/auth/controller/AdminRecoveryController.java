package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.record.recovery.AdminRecoveryRequest;
import vn.vietroute.auth.dto.record.recovery.AdminRecoveryResponse;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AdminRecoveryService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Route auth cứu hộ không thể bị policy quản trị khóa; CSRF/JWT/quota và kiểm chính chủ vẫn bắt buộc. */
@RestController
@RequestMapping(Routes.AUTH)
@AccessFunction(group = Groups.AUTH, authenticated = true)
public class AdminRecoveryController extends BaseController {

    private final AdminRecoveryService recovery;

    public AdminRecoveryController(AdminRecoveryService recovery, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.recovery = recovery;
    }

    @PostMapping(Routes.AUTH_ADMIN_RECOVERY)
    @Operation(
        summary = "Khôi phục quyền tài khoản bootstrap",
        description = "Chỉ chính username bootstrap cấu hình trên server, xác minh mật khẩu hiện tại; không cần mapping ADMIN/ACCESS còn tồn tại. Phục hồi registry khai báo, role ADMIN và mapping, giữ mật khẩu/trạng thái account, ghi lịch sử nguyên tử và thu hồi phiên. Phải đổi mật khẩu tạm trước; không áp dụng cho ADMIN khác."
    )
    public ApiResult<AdminRecoveryResponse> recover(
        @Valid @RequestBody AdminRecoveryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            recovery.recover(body, actor, (String) request.getAttribute(Http.TRACE_ATTRIBUTE))
        );
    }
}
