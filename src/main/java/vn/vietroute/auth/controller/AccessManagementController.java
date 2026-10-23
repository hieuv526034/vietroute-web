package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import vn.vietroute.auth.dto.request.AccessPolicyRequest;
import vn.vietroute.auth.dto.request.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessRegistryResponse;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.AccessTargetResponse;
import vn.vietroute.auth.security.AccessTargets;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Quản lý cấu hình quyền; mọi mutation đi qua BaseController và service có kiểm quyền độc lập. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(name = "Cấu hình phân quyền", description = "ADMIN quản lý registry và chính sách siết thêm menu/endpoint")
public class AccessManagementController extends BaseController {

    private final AccessManagementService access;

    private final AccessResourceService resources;

    private final ObjectProvider<RequestMappingHandlerMapping> mapping;

    public AccessManagementController(
        AccessManagementService access,
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
        description = "ADMIN + ACCESS_READ, giới hạn 512 mã mỗi loại"
    )
    public ApiResult<List<AccessRegistryResponse>> registry(@PathVariable String kind, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> access.registry(kind));
    }

    @PostMapping(Routes.ACCESS_REGISTRY_ITEM)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo role hoặc quyền hành động",
        description = "ADMIN + ACCESS_CREATE; mã role in hoa, quyền RESOURCE_ACTION; mã không đổi tên"
    )
    public ApiResult<List<AccessRegistryResponse>> create(
        @PathVariable String kind,
        @PathVariable String code,
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
        description = "ADMIN + ACCESS_UPDATE; khóa optimistic registry version, không vô hiệu hóa mã lõi"
    )
    public ApiResult<List<AccessRegistryResponse>> update(
        @PathVariable String kind,
        @PathVariable String code,
        @Valid @RequestBody AccessRegistryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.save(kind, code, body, actor, false));
    }

    @GetMapping(Routes.ACCESS_TARGETS)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_READ')")
    @Operation(
        summary = "Inventory menu/endpoint có thể cấu hình",
        description = "Chỉ route thật trên server, không cho thay chính sách auth/public/metrics/cứu hộ"
    )
    public ApiResult<List<AccessTargetResponse>> targets(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> AccessTargets.inventory(mapping.getObject()));
    }

    /** Danh bạ đã đồng bộ có phân trang; chỉ đọc, không nhận URL/public flag từ client. */
    @GetMapping(Routes.ACCESS_RESOURCES)
    @Operation(
        summary = "Danh bạ tài nguyên UI/API đã đồng bộ",
        description = "ADMIN + ACCESS_READ; công khai và cứu hộ chỉ đọc, không tự cấp quyền"
    )
    public ApiResult<PageResponse<AccessResourceResponse>> resources(
        @Valid @ModelAttribute PageRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> resources.page(page));
    }

    @GetMapping(Routes.ACCESS_POLICIES)
    @Operation(summary = "Xem chính sách menu/endpoint", description = "ADMIN + ACCESS_READ")
    public ApiResult<List<AccessPolicyResponse>> policies(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, access::policies);
    }

    @PostMapping(Routes.ACCESS_POLICIES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo chính sách bổ sung",
        description = "ADMIN + ACCESS_CREATE, required permissions là all-of, roles là any-of; không mở rộng quyền trong code"
    )
    public ApiResult<List<AccessPolicyResponse>> createPolicy(
        @Valid @RequestBody AccessPolicyRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            access.savePolicy(0, body, actor, AccessTargets.inventory(mapping.getObject()))
        );
    }

    @PutMapping(Routes.ACCESS_POLICY_ITEM)
    @Operation(
        summary = "Sửa chính sách với version",
        description = "ADMIN + ACCESS_UPDATE; thay đổi áp dụng request tiếp theo trên mọi node"
    )
    public ApiResult<List<AccessPolicyResponse>> updatePolicy(
        @PathVariable long id,
        @Valid @RequestBody AccessPolicyRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            access.savePolicy(id, body, actor, AccessTargets.inventory(mapping.getObject()))
        );
    }

    @DeleteMapping(Routes.ACCESS_POLICY_ITEM)
    @Operation(
        summary = "Xóa chính sách bổ sung",
        description = "ADMIN + ACCESS_DELETE; truyền version, quyền nền vẫn bắt buộc"
    )
    public ApiResult<List<AccessPolicyResponse>> deletePolicy(
        @PathVariable long id,
        @RequestParam long version,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> access.deletePolicy(id, version));
    }
}
