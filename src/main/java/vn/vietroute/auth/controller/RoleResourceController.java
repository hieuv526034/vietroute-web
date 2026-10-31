package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.dto.record.role.RoleResourcesRequest;
import vn.vietroute.auth.dto.record.role.RoleResourcesResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.service.RoleResourceService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API gán tài nguyên từ cây UI; route public/tự phục vụ không được biến thành quyền do admin cấu hình. */
@RestController
@RequestMapping(Routes.ACCESS)
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, actions = { PermissionAction.READ })
public class RoleResourceController extends BaseController {

    private final RoleResourceService grants;
    private final AccessResourceSynchronizer metadata;

    public RoleResourceController(
        RoleResourceService grants,
        AccessResourceSynchronizer metadata,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.grants = grants;
        this.metadata = metadata;
    }

    @GetMapping(Routes.RESOURCE_CATALOG_ACTION)
    @Operation(
        summary = "Danh bạ menu/API hiện tại cho cây gán role",
        description = "ACCESS_READ; metadata nguồn, public API chỉ xem; không chứa dữ liệu người dùng"
    )
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public ApiResult<List<AccessResourceResponse>> catalog(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, metadata::inventory);
    }

    @GetMapping(Routes.ROLE_RESOURCES_ACTION)
    @Operation(
        summary = "Xem menu/API của role",
        description = "Tập target chính xác và version; tài nguyên mới không tự cấp cho role thường"
    )
    public ApiResult<RoleResourcesResponse> get(
        @Parameter(description = "Mã role từ registry") @PathVariable Role role,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> grants.get(role));
    }

    @PutMapping(Routes.ROLE_RESOURCES_ACTION)
    @Operation(
        summary = "Gán menu/API cho role",
        description = "ACCESS_ASSIGN; không sửa ADMIN, không gán public/self-service/target lạ hoặc vượt quyền actor; CAS và lịch sử nguyên tử"
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.ASSIGN })
    public ApiResult<RoleResourcesResponse> replace(
        @Parameter(description = "Mã role từ registry") @PathVariable Role role,
        @Valid @RequestBody RoleResourcesRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> grants.replace(role, body, actor));
    }
}
