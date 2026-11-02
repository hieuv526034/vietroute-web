package vn.vietroute.business.catalog.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
import vn.vietroute.business.catalog.dto.request.CatalogItemRequest;
import vn.vietroute.business.catalog.dto.response.CatalogItemResponse;
import vn.vietroute.business.catalog.dto.search.CatalogItemLookupSearch;
import vn.vietroute.business.catalog.dto.search.CatalogItemSearch;
import vn.vietroute.business.catalog.service.CatalogItemService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.validation.RequestGroups;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Controller danh mục làm ví dụ hoàn chỉnh để các thành viên triển khai module sau theo cùng chuẩn. */
@RestController
@Tag(name = "Catalog", description = "Danh mục vi/en, phân trang và audit")
public class CatalogItemAPI extends BaseController {

    private final CatalogItemService catalogs;

    public CatalogItemAPI(CatalogItemService catalogs, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.catalogs = catalogs;
    }

    @GetMapping(Routes.CATALOG_BY_TYPE)
    @Operation(
        summary = "Tra cứu danh mục",
        description = "Công khai, chỉ trả mục đang hoạt động. page bắt đầu từ 0, size tối đa 100. Tên trả theo locale."
    )
    public ApiResult<PageResponse<CatalogItemResponse>> page(
        @Parameter(description = "Nhóm danh mục cần tra cứu", example = "VEHICLE_TYPE") @PathVariable CatalogType type,
        @Valid @ModelAttribute CatalogItemLookupSearch page,
        HttpServletRequest request
    ) {
        // @Valid kiểm tra phân trang trước; service/repository chỉ đọc danh mục đang hoạt động.
        return execute(SuccessCode.OK, request, () -> catalogs.lookup(type, page, locale(request)));
    }

    @PostMapping(Routes.ADMIN_CATALOGS)
    @Operation(
        summary = "Tạo mục danh mục",
        description = "Cần grant POST đúng endpoint trong DB. Server tự sinh mã nếu null; tên vi/en bắt buộc; code trùng trả 409; audit lấy từ principal."
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<CatalogItemResponse> create(
        @Validated(RequestGroups.Create.class) @RequestBody CatalogItemRequest body,
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
        description = "Cần grant GET đúng endpoint trong DB; xem cả bản đã xóa mềm, không thay đổi API tra cứu công khai"
    )
    public ApiResult<CatalogItemResponse> find(
        @Parameter(description = "ID danh mục, số nguyên dương", example = "1") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.find(id, locale(request)));
    }

    @PutMapping(Routes.ADMIN_CATALOG_BY_ID)
    @Operation(
        summary = "Sửa danh mục",
        description = "Cần grant PUT đúng endpoint trong DB; version cũ trả COM008/409"
    )
    public ApiResult<CatalogItemResponse> update(
        @Parameter(description = "ID danh mục, số nguyên dương", example = "1") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Update.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.update(id, body, actor, locale(request)));
    }

    @DeleteMapping(Routes.ADMIN_CATALOG_BY_ID)
    @Operation(
        summary = "Xóa mềm danh mục",
        description = "Cần grant DELETE đúng endpoint và body chứa version. Bản đã xóa biến mất khỏi tra cứu công khai, audit vẫn giữ."
    )
    public ApiResult<CatalogItemResponse> delete(
        @Parameter(description = "ID danh mục, số nguyên dương", example = "1") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Delete.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.delete(id, body.getVersion(), actor, locale(request)));
    }

    @GetMapping(Routes.ADMIN_VEHICLE_TYPES)
    @Operation(summary = "Danh sách loại xe", description = "Cần grant GET đúng endpoint trong DB; phân trang server.")
    public ApiResult<PageResponse<CatalogItemResponse>> listVehicleType(
        @Valid @ModelAttribute CatalogItemSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.page(CatalogType.VEHICLE_TYPE, page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_VEHICLE_TYPES + Routes.BY_ID)
    @Operation(
        summary = "Chi tiết loại xe",
        description = "ID thuộc loại khác trả 404; GET menu không cấp GET dữ liệu."
    )
    public ApiResult<CatalogItemResponse> findVehicleType(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.find(CatalogType.VEHICLE_TYPE, id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_VEHICLE_TYPES)
    @Operation(
        summary = "Tạo loại xe",
        description = "Type phải khớp tài nguyên server; actor/audit không nhận từ client."
    )
    public ApiResult<CatalogItemResponse> createVehicleType(
        @Validated(RequestGroups.Create.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            catalogs.create(CatalogType.VEHICLE_TYPE, body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_VEHICLE_TYPES + Routes.BY_ID)
    @Operation(
        summary = "Sửa loại xe",
        description = "Giữ code/type; kiểm version trong transaction, xung đột trả 409."
    )
    public ApiResult<CatalogItemResponse> updateVehicleType(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Update.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            catalogs.update(CatalogType.VEHICLE_TYPE, id, body, actor, locale(request))
        );
    }

    @DeleteMapping(Routes.ADMIN_VEHICLE_TYPES + Routes.BY_ID)
    @Operation(summary = "Ngừng dùng loại xe", description = "Xóa mềm, giữ tham chiếu; WRITE không cấp DELETE.")
    public ApiResult<CatalogItemResponse> deleteVehicleType(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Delete.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            catalogs.delete(CatalogType.VEHICLE_TYPE, id, body.getVersion(), actor, locale(request))
        );
    }

    @GetMapping(Routes.ADMIN_BOOKING_SOURCES)
    @Operation(
        summary = "Danh sách nguồn đặt chỗ",
        description = "Cần grant GET đúng endpoint trong DB; phân trang server."
    )
    public ApiResult<PageResponse<CatalogItemResponse>> listBookingSource(
        @Valid @ModelAttribute CatalogItemSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.page(CatalogType.BOOKING_SOURCE, page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @Operation(
        summary = "Chi tiết nguồn đặt chỗ",
        description = "ID thuộc loại khác trả 404; GET menu không cấp GET dữ liệu."
    )
    public ApiResult<CatalogItemResponse> findBookingSource(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> catalogs.find(CatalogType.BOOKING_SOURCE, id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_BOOKING_SOURCES)
    @Operation(
        summary = "Tạo nguồn đặt chỗ",
        description = "Type phải khớp tài nguyên server; actor/audit không nhận từ client."
    )
    public ApiResult<CatalogItemResponse> createBookingSource(
        @Validated(RequestGroups.Create.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            catalogs.create(CatalogType.BOOKING_SOURCE, body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @Operation(
        summary = "Sửa nguồn đặt chỗ",
        description = "Giữ code/type; kiểm version trong transaction, xung đột trả 409."
    )
    public ApiResult<CatalogItemResponse> updateBookingSource(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Update.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            catalogs.update(CatalogType.BOOKING_SOURCE, id, body, actor, locale(request))
        );
    }

    @DeleteMapping(Routes.ADMIN_BOOKING_SOURCES + Routes.BY_ID)
    @Operation(summary = "Ngừng dùng nguồn đặt chỗ", description = "Xóa mềm, giữ tham chiếu; WRITE không cấp DELETE.")
    public ApiResult<CatalogItemResponse> deleteBookingSource(
        @Parameter(description = "ID danh mục dương") @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Delete.class) @RequestBody CatalogItemRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            catalogs.delete(CatalogType.BOOKING_SOURCE, id, body.getVersion(), actor, locale(request))
        );
    }
}
