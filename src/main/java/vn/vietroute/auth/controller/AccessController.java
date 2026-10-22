package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.UserRolesRequest;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.dto.response.RolePermissionsResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;

/** API quản trị quyền gọi service bảo vệ theo chức năng; không nhận actor/audit từ JSON. */
@RestController
@RequestMapping("/api/v1/admin/access")
@Tag(name = "Phân quyền", description = "Quản lý role và quyền chức năng, không bỏ qua phạm vi dữ liệu nghiệp vụ")
public class AccessController extends BaseController {

    private final AccessService access;

    public AccessController(AccessService access, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.access = access;
    }

    @GetMapping("/permissions")
    @Operation(summary = "Liệt kê quyền chức năng", description = "Yêu cầu ACCESS_READ")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> permissions(
        Locale locale,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.permissions(locale));
    }

    @GetMapping("/roles")
    @Operation(summary = "Xem quyền các role", description = "Yêu cầu ACCESS_READ; version dùng khi cập nhật")
    public ResponseEntity<ApiResponse<List<RolePermissionsResponse>>> roles(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, access::roles);
    }

    @PutMapping("/roles/{role}/permissions")
    @Operation(
        summary = "Thay quyền chức năng của role",
        description = "Yêu cầu ACCESS_WRITE; không sửa role ADMIN; version cũ trả 409"
    )
    public ResponseEntity<ApiResponse<RolePermissionsResponse>> replace(
        @PathVariable Role role,
        @Valid @RequestBody RolePermissionsRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.replace(role, body, actor));
    }

    @PutMapping("/users/{id}/roles")
    @Operation(
        summary = "Gán role cho tài khoản",
        description = "Yêu cầu ACCESS_WRITE; thu hồi token cũ, không tự sửa role hoặc xóa admin cuối"
    )
    public ResponseEntity<ApiResponse<UserResponse>> assign(
        @PathVariable long id,
        @Valid @RequestBody UserRolesRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.assign(id, body, actor));
    }
}
