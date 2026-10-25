package vn.vietroute.business.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.route.dto.request.RouteWriteRequest;
import vn.vietroute.business.route.dto.response.RouteResponse;
import vn.vietroute.business.route.service.RouteService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP tuyến chỉ nhận trường cho phép; scope/actor và transaction thuộc service. */
@RestController
@AccessFunction(group = Groups.ROUTE, function = Functions.ROUTE)
public class RouteController extends BaseController {

    private final RouteService service;

    public RouteController(RouteService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_ROUTES)
    @AccessFunction(group = Groups.ROUTE, actions = { PermissionAction.READ })
    @Operation(
        summary = "Tra tuyến một chiều",
        description = "ROUTE_READ và scope hiện hành; query/active/carrierId/page/size lọc tại DB"
    )
    public ApiResult<PageResponse<RouteResponse>> page(
        @Valid @ModelAttribute CarrierSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(search));
    }

    @GetMapping(Routes.ADMIN_ROUTES + Routes.BY_ID)
    @AccessFunction(group = Groups.ROUTE, actions = { PermissionAction.READ })
    @Operation(summary = "Chi tiết tuyến và điểm", description = "ID ngoài scope trả 404; READ độc lập VIEW")
    public ApiResult<RouteResponse> find(
        @Parameter(description = "ID tuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @GetMapping(Routes.ADMIN_ROUTES + Routes.CARRIER_CHOICES)
    @AccessFunction(group = Groups.ROUTE, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Chọn nhà xe để tạo tuyến",
        description = "Chỉ ID/mã/tên nhà xe active có ROUTE_CREATE trong scope, không yêu cầu CARRIER_READ"
    )
    public ApiResult<PageResponse<CarrierChoiceResponse>> choices(
        @Valid @ModelAttribute CarrierSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.choices(search));
    }

    @PostMapping(Routes.ADMIN_ROUTES)
    @AccessFunction(group = Groups.ROUTE, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Tạo tuyến và thứ tự điểm",
        description = "2–32 điểm khác nhau, giờ/giá không giảm, offset phút cho phép qua đêm; lưu nguyên tử"
    )
    public ApiResult<RouteResponse> create(
        @Valid @RequestBody RouteWriteRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_ROUTES + Routes.BY_ID)
    @AccessFunction(group = Groups.ROUTE, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Sửa hoặc ngừng dùng mẫu tuyến",
        description = "Giữ carrier/code/scope; bắt buộc version, không sửa snapshot chuyến đã tạo"
    )
    public ApiResult<RouteResponse> update(
        @Parameter(description = "ID tuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody RouteWriteRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.update(id, body, actor));
    }
}
