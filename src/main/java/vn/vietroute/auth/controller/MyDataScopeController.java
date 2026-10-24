package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.response.DataScopeResponse;
import vn.vietroute.auth.dto.response.ScopePermissionsResponse;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.DataScopeService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Danh sách membership của principal để ứng dụng nhân viên chọn, không nhận userId hoặc scope claim từ client. */
@RestController
@RequestMapping(Routes.AUTH)
@AccessFunction(group = Groups.PROFILE, authenticated = true)
public class MyDataScopeController extends BaseController {

    private final DataScopeService scopes;

    public MyDataScopeController(DataScopeService scopes, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.scopes = scopes;
    }

    @GetMapping(Routes.ACCESS_SCOPE_PERMISSIONS)
    @Operation(
        summary = "Quyền của chính tài khoản trong scope",
        description = "JWT/tokenVersion và account/scope/membership/role/quyền active từ DB; không union quyền scope khác. UI giao với capabilities/policy chung; không thay kiểm DataScopeGuard và WHERE scope khi gọi API nghiệp vụ"
    )
    public ApiResult<ScopePermissionsResponse> permissions(
        @Parameter(description = "ID scope đã chọn và sẽ được DB kiểm membership", example = "1") @PathVariable long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.permissions(id, actor));
    }

    @GetMapping(Routes.ACCESS_SCOPES)
    @Operation(
        summary = "Scope quản trị của chính tài khoản",
        description = "JWT hợp lệ; scope/membership/account đang bật, không có userId đầu vào. Không thay API tra cứu toàn bộ nhà xe/chuyến công khai của khách"
    )
    public ApiResult<List<DataScopeResponse>> mine(
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.mine(actor));
    }
}
