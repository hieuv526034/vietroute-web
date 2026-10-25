package vn.vietroute.business.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogDeleteRequest;
import vn.vietroute.business.catalog.dto.request.CatalogManageUpdateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogSearchRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.service.BookingSourceCatalogService;
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

/** API nguồn đặt chỗ: quyền riêng ở HTTP/service, kiểm type/ID để không sửa chéo danh mục. */
@RestController
@AccessFunction(group = Groups.BOOKING_SOURCE, function = Functions.BOOKING_SOURCE)
public class BookingSourceCatalogController extends BaseController {

    private final BookingSourceCatalogService catalogs;

    public BookingSourceCatalogController(
        BookingSourceCatalogService catalogs,
        ApiResponses responses,
        ApiExceptionMapper exceptions
    ) {
        super(responses, exceptions);
        this.catalogs = catalogs;
    }

    @GetMapping(Routes.ADMIN_BOOKING_SOURCES)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        actions = { PermissionAction.READ },
        permissions = { Permissions.CATALOG_READ }
    )
    @Operation(
        summary = "Danh sách nguồn đặt chỗ",
        description = "READ riêng hoặc CATALOG_READ tương thích; phân trang server."
    )
    public ApiResult<PageResponse<CatalogResponse>> page(
        @Valid @ModelAttribute CatalogSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.page(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        actions = { PermissionAction.READ },
        permissions = { Permissions.CATALOG_READ }
    )
    @Operation(
        summary = "Chi tiết nguồn đặt chỗ",
        description = "ID thuộc loại khác trả 404; quyền VIEW không thay READ."
    )
    public ApiResult<CatalogResponse> find(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.find(id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_BOOKING_SOURCES)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        actions = { PermissionAction.CREATE },
        permissions = { Permissions.CATALOG_CREATE, Permissions.CATALOG_WRITE }
    )
    @Operation(
        summary = "Tạo nguồn đặt chỗ",
        description = "Type phải khớp tài nguyên server; actor/audit không nhận từ client."
    )
    public ApiResult<CatalogResponse> create(
        @Valid @RequestBody CatalogCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            catalogs.create(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        actions = { PermissionAction.UPDATE },
        permissions = { Permissions.CATALOG_UPDATE, Permissions.CATALOG_WRITE }
    )
    @Operation(
        summary = "Sửa nguồn đặt chỗ",
        description = "Giữ code/type; kiểm version trong transaction, xung đột trả 409."
    )
    public ApiResult<CatalogResponse> update(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Valid @RequestBody CatalogManageUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.update(id, body, actor, locale(request)));
    }

    @DeleteMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        actions = { PermissionAction.DELETE },
        permissions = { Permissions.CATALOG_DELETE }
    )
    @Operation(summary = "Ngừng dùng nguồn đặt chỗ", description = "Xóa mềm, giữ tham chiếu; WRITE không cấp DELETE.")
    public ApiResult<CatalogResponse> delete(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Valid @RequestBody CatalogDeleteRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.delete(id, body.getVersion(), actor, locale(request)));
    }
}
