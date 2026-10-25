package vn.vietroute.business.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.trip.dto.request.DriverProgressRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.service.DriverTripService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API tài xế tách staff: ID user luôn lấy principal server, không nhận từ query/body. */
@RestController
@AccessFunction(group = Groups.DRIVER_TRIP, function = Functions.DRIVER_TRIP)
public class DriverTripController extends BaseController {

    private final DriverTripService service;

    public DriverTripController(DriverTripService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.DRIVER_TRIPS)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.READ })
    @Operation(
        summary = "Chuyến được giao cho tài xế",
        description = "DRIVER_TRIP_READ và global/scoped DRIVER hiện hành, lọc/count/page trên SQL theo chính driverUserId; không trả DRAFT"
    )
    public ApiResult<PageResponse<TripResponse>> page(
        @Valid @ModelAttribute TripSearchRequest search,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(search, actor));
    }

    @GetMapping(Routes.DRIVER_TRIPS + Routes.BY_ID)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.READ })
    @Operation(
        summary = "Chi tiết chuyến được giao",
        description = "ID ngoài phân công trả 404, kể cả cùng nhà xe hoặc có ADMIN"
    )
    public ApiResult<TripResponse> find(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id, actor));
    }

    @PutMapping(Routes.DRIVER_TRIPS + Routes.TRIP_PROGRESS)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Cập nhật tiến độ chuyến đúng tài xế",
        description = "START tại đầu, ARRIVE/DEPART theo thứ tự, COMPLETE tại cuối; version bắt buộc, actual timestamp server, event cùng transaction"
    )
    public ApiResult<TripResponse> progress(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody DriverProgressRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.update(id, body, actor));
    }
}
