package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.ManagedAccountCreateRequest;
import vn.vietroute.auth.dto.request.ManagedAccountSearchRequest;
import vn.vietroute.auth.dto.request.ManagedAccountStateRequest;
import vn.vietroute.auth.dto.response.ManagedAccountAccessResponse;
import vn.vietroute.auth.dto.response.ManagedAccountResponse;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessFunction.Match;
import vn.vietroute.auth.service.ManagedAccountService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP chỉ nhận DTO đã validate; mọi audit, quyền cấp role và transaction thuộc service. */
@RestController
@RequestMapping(Routes.ACCOUNTS)
@Tag(name = "Quản lý tài khoản", description = "Admin cấp tài khoản tài xế/người dùng, khóa/mở và xem quyền thực tế")
@AccessFunction(
    group = Groups.ACCOUNTS,
    function = Functions.ACCOUNTS,
    permissions = { Permissions.ACCOUNT_READ },
    adminOnly = true
)
public class ManagedAccountController extends BaseController {

    private final ManagedAccountService accounts;

    private final AccessResourceSynchronizer metadata;

    public ManagedAccountController(
        ManagedAccountService accounts,
        AccessResourceSynchronizer metadata,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.accounts = accounts;
        this.metadata = metadata;
    }

    @GetMapping
    @Operation(
        summary = "Tìm tài khoản",
        description = "ADMIN + ACCOUNT_READ; lọc username/role/trạng thái, phân trang server, không trả credential hoặc system"
    )
    public ApiResult<PageResponse<ManagedAccountResponse>> page(
        @Valid ManagedAccountSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> accounts.page(page));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Cấp tài khoản và gán role",
        description = "ADMIN + ACCOUNT_CREATE + ACCESS_ASSIGN; tài khoản active, bắt đổi mật khẩu tạm; không tạo membership nhà xe, không gửi mail hoặc trả password"
    )
    @AccessFunction(
        group = Groups.ACCOUNTS,
        permissions = { Permissions.ACCOUNT_CREATE, Permissions.ACCESS_ASSIGN },
        match = Match.ALL
    )
    public ApiResult<ManagedAccountResponse> create(
        @Valid @RequestBody ManagedAccountCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.OK, request, () -> accounts.create(body, actor));
    }

    @PutMapping(Routes.ACCOUNT_STATE)
    @Operation(
        summary = "Khóa hoặc mở tài khoản",
        description = "ADMIN + ACCOUNT_UPDATE; version bắt buộc, thu hồi mọi JWT/challenge; cấm system/bản thân/ADMIN/chờ kích hoạt"
    )
    @AccessFunction(group = Groups.ACCOUNTS, permissions = { Permissions.ACCOUNT_UPDATE })
    public ApiResult<ManagedAccountResponse> state(
        @Parameter(description = "ID tài khoản cần khóa/mở", example = "2") @PathVariable long id,
        @Valid @RequestBody ManagedAccountStateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> accounts.state(id, body, actor));
    }

    @GetMapping(Routes.ACCOUNT_ACCESS)
    @Operation(
        summary = "Xem menu và thao tác của tài khoản",
        description = "ADMIN + ACCOUNT_READ; tính từ quyền DB active và policy hiện tại, không tạo JWT, không trả quyền giả khi tài khoản tắt/cần đổi mật khẩu"
    )
    public ApiResult<ManagedAccountAccessResponse> effective(
        @Parameter(description = "ID tài khoản cần xem quyền", example = "2") @PathVariable long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> accounts.effective(id, metadata.inventory()));
    }
}
