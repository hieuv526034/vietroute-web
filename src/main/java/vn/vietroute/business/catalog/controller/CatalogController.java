package vn.vietroute.business.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogDeleteRequest;
import vn.vietroute.business.catalog.dto.request.CatalogUpdateRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.service.CatalogService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Controller danh mục làm ví dụ hoàn chỉnh để các thành viên triển khai module sau theo cùng chuẩn. */
@RestController
@Tag(name = "Catalog", description = "Danh mục vi/en, phân trang và audit")
@AccessFunction(group = Groups.CATALOG)
public class CatalogController extends BaseController {

    private final CatalogService catalogs;

    public CatalogController(CatalogService catalogs, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.catalogs = catalogs;
    }

    @GetMapping(Routes.CATALOG_BY_TYPE)
    @Operation(
        summary = "Tra cứu danh mục",
        description = "Công khai, chỉ trả mục đang hoạt động. page bắt đầu từ 0, size tối đa 100. Tên trả theo locale."
    )
    public ApiResult<PageResponse<CatalogResponse>> page(
        @PathVariable CatalogType type,
        @Valid @ModelAttribute PageRequest page,
        HttpServletRequest request
    ) {
        // @Valid kiểm tra phân trang trước; service/repository chỉ đọc danh mục đang hoạt động.
        return execute(SuccessCode.OK, request, () -> catalogs.page(type, page, locale(request)));
    }

    @PostMapping(Routes.ADMIN_CATALOGS)
    @Operation(
        summary = "Tạo mục danh mục",
        description = "Yêu cầu CATALOG_CREATE (hoặc CATALOG_WRITE tương thích). Server tự sinh mã nếu null; tên vi/en bắt buộc; code trùng trả 409; audit lấy từ principal."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @AccessFunction(group = Groups.CATALOG, permissions = { Permissions.CATALOG_CREATE, Permissions.CATALOG_WRITE })
    public ApiResult<CatalogResponse> create(
        @Valid @RequestBody CatalogCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        // Quyền/audit lấy từ principal, insert nằm trong transaction; mã trùng được base đổi thành HTTP 409.
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            catalogs.create(body, actor, locale(request))
        );
    }

    @GetMapping(Routes.ADMIN_CATALOG_BY_ID)
    @Operation(
        summary = "Đọc danh mục quản trị",
        description = "Yêu cầu CATALOG_READ; xem cả bản đã xóa mềm, không thay đổi API tra cứu công khai"
    )
    @AccessFunction(group = Groups.CATALOG, permissions = { Permissions.CATALOG_READ })
    public ApiResult<CatalogResponse> find(
        @PathVariable @Min(value = 1, message = "{validation.invalid}") long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.find(id, locale(request)));
    }

    @PutMapping(Routes.ADMIN_CATALOG_BY_ID)
    @Operation(
        summary = "Sửa danh mục",
        description = "Yêu cầu CATALOG_UPDATE (hoặc CATALOG_WRITE tương thích); version cũ trả COM008/409"
    )
    @AccessFunction(group = Groups.CATALOG, permissions = { Permissions.CATALOG_UPDATE, Permissions.CATALOG_WRITE })
    public ApiResult<CatalogResponse> update(
        @PathVariable @Min(value = 1, message = "{validation.invalid}") long id,
        @Valid @RequestBody CatalogUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.update(id, body, actor, locale(request)));
    }

    @DeleteMapping(Routes.ADMIN_CATALOG_BY_ID)
    @Operation(
        summary = "Xóa mềm danh mục",
        description = "Yêu cầu CATALOG_DELETE, body chứa version; WRITE không có quyền xóa. Bản đã xóa biến mất khỏi tra cứu công khai, audit vẫn giữ."
    )
    @AccessFunction(group = Groups.CATALOG, permissions = { Permissions.CATALOG_DELETE })
    public ApiResult<CatalogResponse> delete(
        @PathVariable @Min(value = 1, message = "{validation.invalid}") long id,
        @Valid @RequestBody CatalogDeleteRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.delete(id, body.getVersion(), actor, locale(request)));
    }
}
