package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.record.scope.DataScopeRequest;
import vn.vietroute.auth.dto.record.scope.DataScopeResponse;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipRequest;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipResponse;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.scope.ScopeMemberSearchRequest;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.DataScopeService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Quản trị scope bảo mật ADMIN-only; không phải API tra cứu nhà xe công khai hoặc booking. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(
    name = "Phạm vi dữ liệu",
    description = "Metadata phạm vi và gán role theo từng membership; không tự tạo nghiệp vụ nhà xe"
)
@AccessFunction(
    group = Groups.ACCESS,
    function = Functions.ACCESS,
    actions = { PermissionAction.READ },
    adminOnly = true
)
public class DataScopeController extends BaseController {

    private final DataScopeService scopes;

    public DataScopeController(DataScopeService scopes, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.scopes = scopes;
    }

    @GetMapping(Routes.ACCESS_SCOPES)
    @Operation(
        summary = "Tra phạm vi bảo mật",
        description = "ADMIN + ACCESS_READ; query/active/page/size, không phải danh sách nhà xe cho khách"
    )
    public ApiResult<PageResponse<DataScopeResponse>> page(
        @Valid @ModelAttribute AccessRegistrySearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.page(search));
    }

    @PostMapping(Routes.ACCESS_SCOPES)
    @ResponseStatus(HttpStatus.CREATED)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.CREATE }, adminOnly = true)
    @Operation(
        summary = "Tạo phạm vi bảo mật",
        description = "ADMIN + ACCESS_CREATE; code bất biến/duy nhất, quota hữu hạn, audit cùng transaction"
    )
    public ApiResult<DataScopeResponse> create(
        @Valid @RequestBody DataScopeRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> scopes.save(0, body, actor));
    }

    @PutMapping(Routes.ACCESS_SCOPE_ITEM)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.UPDATE }, adminOnly = true)
    @Operation(
        summary = "Sửa trạng thái/tên phạm vi",
        description = "ADMIN + ACCESS_UPDATE; giữ code, version cũ trả 409. Tắt scope chặn mọi scoped API, khác trạng thái nhà xe ngừng nhận booking"
    )
    public ApiResult<DataScopeResponse> update(
        @Parameter(description = "ID scope server cấp", example = "1") @PathVariable long id,
        @Valid @RequestBody DataScopeRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.save(id, body, actor));
    }

    @GetMapping(Routes.ACCESS_SCOPE_MEMBERS)
    @Operation(
        summary = "Tra membership trong phạm vi",
        description = "ADMIN + ACCESS_READ; userId tùy chọn/page/size, không credential, mapping role có thể gồm role đang tắt"
    )
    public ApiResult<PageResponse<ScopeMembershipResponse>> members(
        @Parameter(description = "ID scope", example = "1") @PathVariable long id,
        @Valid @ModelAttribute ScopeMemberSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.members(id, search));
    }

    @PutMapping(Routes.ACCESS_SCOPE_MEMBER)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.ASSIGN }, adminOnly = true)
    @Operation(
        summary = "Gán/tắt role riêng tại phạm vi",
        description = "ADMIN + ACCESS_ASSIGN; role phải thuộc chính user toàn cục, không ADMIN/system/self, version 0 tạo mới và >=1 để cập nhật; ghi lịch sử nguyên tử, không phát JWT scope"
    )
    public ApiResult<ScopeMembershipResponse> assign(
        @Parameter(description = "ID scope", example = "1") @PathVariable long id,
        @Parameter(description = "ID tài khoản đã được cấp role toàn cục", example = "2") @PathVariable long userId,
        @Valid @RequestBody ScopeMembershipRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> scopes.assign(id, userId, body, actor));
    }
}
