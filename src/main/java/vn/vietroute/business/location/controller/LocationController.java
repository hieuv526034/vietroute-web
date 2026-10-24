package vn.vietroute.business.location.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.location.dto.request.LocationCreateRequest;
import vn.vietroute.business.location.dto.request.LocationLookupRequest;
import vn.vietroute.business.location.dto.request.LocationSearchRequest;
import vn.vietroute.business.location.dto.request.LocationUpdateRequest;
import vn.vietroute.business.location.dto.request.ProvinceCreateRequest;
import vn.vietroute.business.location.dto.request.ProvinceLookupRequest;
import vn.vietroute.business.location.dto.request.ProvinceSearchRequest;
import vn.vietroute.business.location.dto.request.ProvinceUpdateRequest;
import vn.vietroute.business.location.dto.response.LocationLookupResponse;
import vn.vietroute.business.location.dto.response.LocationResponse;
import vn.vietroute.business.location.dto.response.ProvinceLookupResponse;
import vn.vietroute.business.location.dto.response.ProvinceResponse;
import vn.vietroute.business.location.service.LocationService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API công khai có projection riêng; API quản trị giữ ADMIN và permission trên cả HTTP lẫn service. */
@RestController
@Tag(name = "Location", description = "Tỉnh/thành và địa điểm dùng chung")
@AccessFunction(group = Groups.LOCATION, function = Functions.LOCATION)
public class LocationController extends BaseController {

    private final LocationService locations;

    public LocationController(LocationService locations, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.locations = locations;
    }

    @GetMapping(Routes.PROVINCES)
    @AccessFunction(group = Groups.LOCATION)
    @Operation(
        summary = "Tra cứu tỉnh/thành công khai",
        description = "Chỉ dữ liệu đang sử dụng; địa điểm còn yêu cầu tỉnh/thành active. page từ 0, size tối đa 100, query nguyên văn; không trả audit."
    )
    public ApiResult<PageResponse<ProvinceLookupResponse>> lookupProvinces(
        @Valid @ModelAttribute ProvinceLookupRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.lookupProvinces(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_PROVINCES)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.READ }, adminOnly = true)
    @Operation(
        summary = "Danh sách tỉnh/thành quản trị",
        description = "ADMIN + LOCATION_READ; query, active và phân trang tại DB. Xem cả dữ liệu đã ngừng sử dụng."
    )
    public ApiResult<PageResponse<ProvinceResponse>> provinces(
        @Valid @ModelAttribute ProvinceSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.provinces(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_PROVINCES_BY_ID)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.READ }, adminOnly = true)
    @Operation(
        summary = "Đọc tỉnh/thành quản trị",
        description = "ADMIN + LOCATION_READ; ID không tồn tại trả COM004/404, gồm cả bản ngừng sử dụng."
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
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.CREATE }, adminOnly = true)
    @Operation(
        summary = "Tạo tỉnh/thành",
        description = "ADMIN + LOCATION_CREATE; mã trùng COM005/409, audit từ principal."
    )
    public ApiResult<ProvinceResponse> createProvince(
        @Valid @RequestBody ProvinceCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            locations.createProvince(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_PROVINCES_BY_ID)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.UPDATE }, adminOnly = true)
    @Operation(
        summary = "Sửa/ngừng sử dụng tỉnh/thành",
        description = "ADMIN + LOCATION_UPDATE; version cũ COM008/409. Giữ mã, không xóa dữ liệu hoặc tham chiếu. Tỉnh tắt ẩn mọi địa điểm con khỏi tra cứu; bật lại giữ trạng thái riêng của từng con."
    )
    public ApiResult<ProvinceResponse> updateProvince(
        @Parameter(description = "ID tỉnh/thành, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody ProvinceUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.updateProvince(id, body, actor, locale(request)));
    }

    @GetMapping(Routes.LOCATIONS)
    @AccessFunction(group = Groups.LOCATION)
    @Operation(
        summary = "Tra cứu địa điểm công khai",
        description = "Chỉ dữ liệu đang sử dụng; địa điểm còn yêu cầu tỉnh/thành active. page từ 0, size tối đa 100, query nguyên văn; không trả audit."
    )
    public ApiResult<PageResponse<LocationLookupResponse>> lookupLocations(
        @Valid @ModelAttribute LocationLookupRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.lookupLocations(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_LOCATIONS)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.READ }, adminOnly = true)
    @Operation(
        summary = "Danh sách địa điểm quản trị",
        description = "ADMIN + LOCATION_READ; query, active và phân trang tại DB. Xem cả dữ liệu đã ngừng sử dụng."
    )
    public ApiResult<PageResponse<LocationResponse>> locations(
        @Valid @ModelAttribute LocationSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.locations(page, locale(request)));
    }

    @GetMapping(Routes.ADMIN_LOCATIONS_BY_ID)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.READ }, adminOnly = true)
    @Operation(
        summary = "Đọc địa điểm quản trị",
        description = "ADMIN + LOCATION_READ; ID không tồn tại trả COM004/404, gồm cả bản ngừng sử dụng."
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
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.CREATE }, adminOnly = true)
    @Operation(
        summary = "Tạo địa điểm",
        description = "ADMIN + LOCATION_CREATE; mã trùng COM005/409, audit từ principal. Tỉnh không tồn tại COM004/404; tỉnh ngừng sử dụng LOC001/409."
    )
    public ApiResult<LocationResponse> createLocation(
        @Valid @RequestBody LocationCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            locations.createLocation(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_LOCATIONS_BY_ID)
    @AccessFunction(group = Groups.LOCATION, actions = { PermissionAction.UPDATE }, adminOnly = true)
    @Operation(
        summary = "Sửa/ngừng sử dụng địa điểm",
        description = "ADMIN + LOCATION_UPDATE; version cũ COM008/409. Giữ mã và tỉnh/thành, không xóa dữ liệu hoặc tham chiếu. Bật địa điểm yêu cầu tỉnh active; nếu không trả LOC001/409."
    )
    public ApiResult<LocationResponse> updateLocation(
        @Parameter(description = "ID địa điểm, số nguyên dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody LocationUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> locations.updateLocation(id, body, actor, locale(request)));
    }
}
