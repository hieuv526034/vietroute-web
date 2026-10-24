package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.AccessUserSearchRequest;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.UserRolesRequest;
import vn.vietroute.auth.dto.response.AccessUserResponse;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.dto.response.RolePermissionsResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AccessService;
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

/** API quản trị quyền gọi service bảo vệ theo chức năng; không nhận actor/audit từ JSON. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(name = "Phân quyền", description = "Quản lý role và quyền chức năng, không bỏ qua phạm vi dữ liệu nghiệp vụ")
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, permissions = { Permissions.ACCESS_READ })
public class AccessController extends BaseController {

    private final AccessService access;

    public AccessController(AccessService access, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.access = access;
    }

    @GetMapping(Routes.PERMISSIONS_ACTION)
    @Operation(summary = "Liệt kê quyền chức năng", description = "Yêu cầu ACCESS_READ")
    public ApiResult<List<PermissionResponse>> permissions(Locale locale, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> access.permissions(locale));
    }

    @GetMapping(Routes.ROLES_ACTION)
    @Operation(summary = "Xem quyền các role", description = "Yêu cầu ACCESS_READ; version dùng khi cập nhật")
    public ApiResult<List<RolePermissionsResponse>> roles(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, access::roles);
    }

    @GetMapping(Routes.USERS_ACTION)
    @Operation(
        summary = "Tra tài khoản để gán role",
        description = "Cần ACCESS_READ, hỗ trợ role được ủy quyền; phân trang/prefix username, không trả credential. Gán role dùng API PUT riêng có version và kiểm quyền."
    )
    public ApiResult<PageResponse<AccessUserResponse>> users(
        @Valid AccessUserSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.users(page));
    }

    @PutMapping(Routes.ROLE_PERMISSIONS_ACTION)
    @Operation(
        summary = "Thay quyền chức năng của role",
        description = "Yêu cầu ACCESS_ASSIGN (hoặc ACCESS_WRITE tương thích); không sửa ADMIN; người được ủy quyền không gán quyền vượt tập quyền của mình; version cũ trả 409"
    )
    @AccessFunction(group = Groups.ACCESS, permissions = { Permissions.ACCESS_ASSIGN, Permissions.ACCESS_WRITE })
    public ApiResult<RolePermissionsResponse> replace(
        @Parameter(
            description = "Mã role từ registry DB, không giới hạn ở role bootstrap",
            example = "OPERATOR"
        ) @PathVariable Role role,
        @Valid @RequestBody RolePermissionsRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.replace(role, body, actor));
    }

    @PutMapping(Routes.USER_ROLES_ACTION)
    @Operation(
        summary = "Gán role cho tài khoản",
        description = "Yêu cầu ACCESS_ASSIGN (hoặc ACCESS_WRITE tương thích) và role ADMIN; thu hồi token cũ, không tự sửa role hoặc xóa admin cuối"
    )
    @AccessFunction(group = Groups.ACCESS, permissions = { Permissions.ACCESS_ASSIGN, Permissions.ACCESS_WRITE })
    public ApiResult<UserResponse> assign(
        @Parameter(description = "ID bản ghi cần thao tác", example = "1") @PathVariable long id,
        @Valid @RequestBody UserRolesRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.assign(id, body, actor));
    }
}
