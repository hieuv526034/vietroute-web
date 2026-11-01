package vn.vietroute.auth.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.RoleService;
import vn.vietroute.auth.service.UserService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API theo bảng gốc; quyền endpoint do interceptor, actor/audit chỉ lấy server. */
@RestController
@Tag(name = "RoleAPI")
public class RoleAPI extends BaseController {

    private final RoleService roles;
    private final UserService users;

    public RoleAPI(RoleService roles, UserService users, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.roles = roles;
        this.users = users;
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_REGISTRY)
    @Operation(summary = "Danh sách vai trò")
    public ApiResult<List<RoleDTO>> all(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, roles::all);
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_REGISTRY_PAGE)
    @Operation(summary = "Tìm và phân trang vai trò")
    public ApiResult<PageResponse<RoleDTO>> search(
        @Valid @ModelAttribute RoleSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.search(search));
    }

    @PostMapping(Routes.ACCESS + Routes.ACCESS_REGISTRY_ITEM)
    @Operation(summary = "Tạo vai trò", description = "nameVi/nameEn bắt buộc; không có grant đến khi gán Function")
    public ApiResult<RoleDTO> create(
        @PathVariable String code,
        @Valid @RequestBody RoleRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> roles.save(code, body, true, actor));
    }

    @PutMapping(Routes.ACCESS + Routes.ACCESS_REGISTRY_ITEM)
    @Operation(
        summary = "Sửa vai trò",
        description = "nameVi/nameEn/version bắt buộc; không sửa role bootstrap được bảo vệ"
    )
    public ApiResult<RoleDTO> update(
        @PathVariable String code,
        @Valid @RequestBody RoleRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.save(code, body, false, actor));
    }

    @DeleteMapping(Routes.ACCESS + Routes.ACCESS_ROLE_ITEM)
    @Operation(summary = "Xóa mềm vai trò chưa dùng")
    public ApiResult<Void> delete(
        @PathVariable String code,
        @Parameter(description = "Version hiện hành") @RequestParam long version,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            roles.delete(code, version, actor);
            return null;
        });
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_ROLE_FUNCTIONS)
    @Operation(summary = "Chức năng đã gán cho vai trò")
    public ApiResult<RoleDTO> functions(@PathVariable String role, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> roles.functions(role));
    }

    @PutMapping(Routes.ACCESS + Routes.ACCESS_ROLE_FUNCTIONS)
    @Operation(
        summary = "Gán chức năng cho vai trò",
        description = "functionCodes/version bắt buộc; tập rỗng thu hồi, trần endpoint kiểm từ DB"
    )
    public ApiResult<RoleDTO> assignFunctions(
        @PathVariable String role,
        @Valid @RequestBody RoleRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.assignFunctions(role, body, actor));
    }

    @GetMapping(Routes.ACCESS + Routes.USERS_ACTION)
    @Operation(summary = "Tìm tài khoản để gán vai trò")
    public ApiResult<PageResponse<UserDTO>> users(
        @Valid @ModelAttribute UserSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> users.search(search));
    }

    @PutMapping(Routes.ACCESS + Routes.USER_ROLES_ACTION)
    @Operation(
        summary = "Gán role toàn cục cho tài khoản",
        description = "roles/version bắt buộc; scoped role bị thu hồi nếu không còn thuộc tập global"
    )
    public ApiResult<UserDTO> assignUser(
        @PathVariable long id,
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.assignUser(id, body, actor));
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_SCOPES)
    @Operation(summary = "Phạm vi nhà xe")
    public ApiResult<PageResponse<RoleDTO>> scopes(
        @Valid @ModelAttribute RoleSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.scopes(search));
    }

    @PutMapping(Routes.ACCESS + Routes.ACCESS_SCOPE_ITEM)
    @Operation(summary = "Sửa trạng thái phạm vi nhà xe")
    public ApiResult<RoleDTO> saveScope(
        @PathVariable long id,
        @Valid @RequestBody RoleRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.saveScope(id, body, actor));
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_SCOPE_MEMBERS)
    @Operation(summary = "Người dùng thuộc phạm vi nhà xe")
    public ApiResult<PageResponse<RoleDTO>> members(
        @PathVariable long id,
        @Valid @ModelAttribute UserSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.members(id, search));
    }

    @PutMapping(Routes.ACCESS + Routes.ACCESS_SCOPE_MEMBER)
    @Operation(
        summary = "Gán scoped role cho người dùng",
        description = "roles/active/version bắt buộc; scoped role phải thuộc global role của cùng người dùng"
    )
    public ApiResult<RoleDTO> assignScope(
        @PathVariable long id,
        @PathVariable long userId,
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.assignScope(id, userId, body, actor));
    }

    @GetMapping(Routes.AUTH + Routes.ACCESS_SCOPES)
    @Operation(summary = "Phạm vi của chính tài khoản")
    public ApiResult<List<RoleDTO>> mine(@AuthenticationPrincipal UserPrincipal actor, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> roles.mine(actor));
    }

    @GetMapping(Routes.AUTH + Routes.ACCESS_SCOPE_PERMISSIONS)
    @Operation(summary = "Endpoint của chính tài khoản trong phạm vi")
    public ApiResult<RoleDTO> scopeTargets(
        @PathVariable long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> roles.scopeTargets(id, actor));
    }
}
