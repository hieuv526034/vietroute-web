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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.LocationRequest;
import vn.vietroute.business.catalog.dto.request.ProvinceRequest;
import vn.vietroute.business.catalog.dto.response.LocationLookupResponse;
import vn.vietroute.business.catalog.dto.response.LocationResponse;
import vn.vietroute.business.catalog.dto.response.ProvinceLookupResponse;
import vn.vietroute.business.catalog.dto.response.ProvinceResponse;
import vn.vietroute.business.catalog.dto.search.LocationLookupSearch;
import vn.vietroute.business.catalog.dto.search.LocationSearch;
import vn.vietroute.business.catalog.dto.search.ProvinceLookupSearch;
import vn.vietroute.business.catalog.dto.search.ProvinceSearch;
import vn.vietroute.business.catalog.service.LocationService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.validation.RequestGroups;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API công khai có projection riêng; API quản trị giữ ADMIN và permission trên cả HTTP lẫn service. */
@RestController
@Tag(name = "Location", description = "Tỉnh/thành và địa điểm dùng chung")
public class LocationAPI extends BaseController {

    private final LocationService locations;

    public LocationAPI(LocationService locations, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.locations = locations;
    }

    @GetMapping(Routes.PROVINCES)
    @Operation(
        summary = "Tra cứu tỉnh/thành công khai",
        description = "Chỉ dữ liệu đang sử dụng; địa điểm còn yêu cầu tỉnh/thành active. page từ 0, size tối đa 100, query nguyên văn; không trả audit."
    )
    public ApiResult<PageResponse<ProvinceLookupResponse>> lookupProvinces(
        @Valid @ModelAttribute ProvinceLookupSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.lookupProvinces(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_PROVINCES)
    @Operation(
        summary = "Danh sách tỉnh/thành quản trị",
        description = "Grant GET endpoint địa điểm; query, active và phân trang tại DB. Xem cả dữ liệu đã ngừng sử dụng."
    )
    public ApiResult<PageResponse<ProvinceResponse>> provinces(
        @Valid @ModelAttribute ProvinceSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.provinces(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_PROVINCES_BY_ID)
    @Operation(
        summary = "Đọc tỉnh/thành quản trị",
        description = "Grant GET endpoint địa điểm; ID không tồn tại trả COM004/404, gồm cả bản ngừng sử dụng."
    )
    public ApiResult<ProvinceResponse> province(
        @Parameter(description = "ID tỉnh/thành, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.province(id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_PROVINCES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo tỉnh/thành",
        description = "Grant POST endpoint địa điểm; mã trùng COM005/409, audit từ principal."
    )
    public ApiResult<ProvinceResponse> createProvince(
        @Validated(RequestGroups.Create.class) @RequestBody ProvinceRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            locations.createProvince(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_PROVINCES_BY_ID)
    @Operation(
        summary = "Sửa/ngừng sử dụng tỉnh/thành",
        description = "Grant PUT endpoint địa điểm; version cũ COM008/409. Giữ mã, không xóa dữ liệu hoặc tham chiếu. Tỉnh tắt ẩn mọi địa điểm con khỏi tra cứu; bật lại giữ trạng thái riêng của từng con."
    )
    public ApiResult<ProvinceResponse> updateProvince(
        @Parameter(description = "ID tỉnh/thành, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Update.class) @RequestBody ProvinceRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.updateProvince(id, body, actor, locale(request)));
    }

    @GetMapping(Routes.LOCATIONS)
    @Operation(
        summary = "Tra cứu địa điểm công khai",
        description = "Chỉ dữ liệu đang sử dụng; địa điểm còn yêu cầu tỉnh/thành active. page từ 0, size tối đa 100, query nguyên văn; không trả audit."
    )
    public ApiResult<PageResponse<LocationLookupResponse>> lookupLocations(
        @Valid @ModelAttribute LocationLookupSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.lookupLocations(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_LOCATIONS)
    @Operation(
        summary = "Danh sách địa điểm quản trị",
        description = "Grant GET endpoint địa điểm; query, active và phân trang tại DB. Xem cả dữ liệu đã ngừng sử dụng."
    )
    public ApiResult<PageResponse<LocationResponse>> locations(
        @Valid @ModelAttribute LocationSearch page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.locations(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_LOCATIONS_BY_ID)
    @Operation(
        summary = "Đọc địa điểm quản trị",
        description = "Grant GET endpoint địa điểm; ID không tồn tại trả COM004/404, gồm cả bản ngừng sử dụng."
    )
    public ApiResult<LocationResponse> location(
        @Parameter(description = "ID địa điểm, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.location(id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_LOCATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo địa điểm",
        description = "Grant POST endpoint địa điểm; mã trùng COM005/409, audit từ principal. Tỉnh không tồn tại COM004/404; tỉnh ngừng sử dụng LOC001/409."
    )
    public ApiResult<LocationResponse> createLocation(
        @Validated(RequestGroups.Create.class) @RequestBody LocationRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            locations.createLocation(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_LOCATIONS_BY_ID)
    @Operation(
        summary = "Sửa/ngừng sử dụng địa điểm",
        description = "Grant PUT endpoint địa điểm; version cũ COM008/409. Giữ mã và tỉnh/thành, không xóa dữ liệu hoặc tham chiếu. Bật địa điểm yêu cầu tỉnh active; nếu không trả LOC001/409."
    )
    public ApiResult<LocationResponse> updateLocation(
        @Parameter(description = "ID địa điểm, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Update.class) @RequestBody LocationRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.updateLocation(id, body, actor, locale(request)));
    }
}
