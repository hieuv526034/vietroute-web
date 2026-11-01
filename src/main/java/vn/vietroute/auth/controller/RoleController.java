package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.access.AccessResourceSearchRequest;
import vn.vietroute.auth.dto.request.registry.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.registry.AccessRegistryPageResponse;
import vn.vietroute.auth.dto.response.registry.AccessRegistryResponse;
import vn.vietroute.auth.dto.response.resource.AccessResourceResponse;
import vn.vietroute.auth.dto.response.resource.AccessTargetResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessTargets;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.auth.service.RoleService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Quản lý cấu hình quyền; mọi mutation đi qua BaseController và service có kiểm quyền độc lập. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(
    name = "Cấu hình phân quyền",
    description = "Quản trị theo quyền DB, quản lý vai trò, danh bạ menu/API; chức năng được quản lý qua Function API"
)
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, actions = { PermissionAction.READ })
public class RoleController extends BaseController {

    private final RoleService access;

    private final AccessResourceService resources;

    private final ObjectProvider<RequestMappingHandlerMapping> mapping;

    public RoleController(
        RoleService access,
        AccessResourceService resources,
        ObjectProvider<RequestMappingHandlerMapping> mapping,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.access = access;
        this.resources = resources;
        this.mapping = mapping;
    }

    @GetMapping(Routes.ACCESS_REGISTRY)
    @Operation(
        summary = "Danh sách registry roles/permissions",
        description = "ACCESS_READ; API tương thích đầy đủ trong trần an toàn, màn registry dùng API phân trang"
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.READ })
    public ApiResult<List<AccessRegistryResponse>> registry(
        @Parameter(description = "Loại registry: roles hoặc permissions", example = "roles") @PathVariable String kind,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.registry(kind));
    }

    @GetMapping(Routes.ACCESS_REGISTRY_PAGE)
    @Operation(
        summary = "Tìm và phân trang registry",
        description = "ACCESS_READ; query nguyên văn trong mã/tên vi/en, active tùy chọn, page/size. Trả quota toàn loại chưa xóa, không tính tombstone role."
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.READ })
    public ApiResult<AccessRegistryPageResponse> registryPage(
        @Parameter(description = "Loại registry roles hoặc permissions", example = "roles") @PathVariable String kind,
        @Valid @ModelAttribute AccessRegistrySearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.registryPage(kind, search));
    }

    @PostMapping(Routes.ACCESS_REGISTRY_ITEM)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo role hoặc quyền hành động",
        description = "ACCESS_CREATE; mã role in hoa, quyền RESOURCE_ACTION; mã không đổi tên"
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.CREATE })
    public ApiResult<List<AccessRegistryResponse>> create(
        @Parameter(description = "Loại registry: roles hoặc permissions", example = "roles") @PathVariable String kind,
        @Parameter(
            description = "Mã registry bất biến; quyền dùng RESOURCE_ACTION",
            example = "CATALOG_READ"
        ) @PathVariable String code,
        @Valid @RequestBody AccessRegistryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            access.save(kind, code, body, actor, true)
        );
    }

    @PutMapping(Routes.ACCESS_REGISTRY_ITEM)
    @Operation(
        summary = "Sửa tên/trạng thái role hoặc quyền",
        description = "ACCESS_UPDATE; khóa optimistic registry version, không vô hiệu hóa mã lõi"
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.UPDATE })
    public ApiResult<List<AccessRegistryResponse>> update(
        @Parameter(description = "Loại registry: roles hoặc permissions", example = "roles") @PathVariable String kind,
        @Parameter(
            description = "Mã registry bất biến; quyền dùng RESOURCE_ACTION",
            example = "CATALOG_READ"
        ) @PathVariable String code,
        @Valid @RequestBody AccessRegistryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.save(kind, code, body, actor, false));
    }

    @GetMapping(Routes.ACCESS_TARGETS)
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    @Operation(
        summary = "Inventory menu/endpoint có thể cấu hình",
        description = "Chỉ route thật trên server, không cho thay chính sách auth/public/metrics/cứu hộ"
    )
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.READ })
    public ApiResult<List<AccessTargetResponse>> targets(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> AccessTargets.inventory(mapping.getObject()));
    }

    /** ADMIN bất biến; role khác chỉ xóa khi không còn user/policy sử dụng và version còn đúng. */
    @DeleteMapping(Routes.ACCESS_ROLE_ITEM)
    @Operation(summary = "Xóa role chưa sử dụng", description = "ACCESS_DELETE; version bắt buộc, liên quan trả 409")
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.DELETE })
    public ApiResult<List<AccessRegistryResponse>> deleteRole(
        @Parameter(
            description = "Mã role bất biến cần xóa, không phải mã quyền",
            example = "OPERATOR"
        ) @PathVariable String code,
        @Parameter(
            description = "Version hiện hành lấy từ response; version cũ trả 409",
            example = "0"
        ) @RequestParam long version,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.deleteRole(code, version, actor));
    }

    /** Danh bạ đã đồng bộ có phân trang; chỉ đọc, không nhận URL/public flag từ client. */
    @GetMapping(Routes.ACCESS_RESOURCES)
    @Operation(
        summary = "Danh bạ tài nguyên UI/API đã đồng bộ",
        description = "ACCESS_READ; lọc kind=MENU/ENDPOINT và query nguyên văn trước phân trang; công khai/cứu hộ chỉ đọc, không tự cấp quyền"
    )
    @AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS_RESOURCES, actions = { PermissionAction.READ })
    public ApiResult<PageResponse<AccessResourceResponse>> resources(
        @Valid @ModelAttribute AccessResourceSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> resources.page(page));
    }
}
