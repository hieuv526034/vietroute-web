package vn.vietroute.business.tracking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.service.CustomerTrackingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Không công bố vị trí cho anonymous hoặc chỉ dựa tripId; booking owner/CONFIRMED là điều kiện bắt buộc. */
@RestController
@AccessFunction(group = Groups.MY_BOOKING, function = Functions.MY_BOOKING, actions = { PermissionAction.READ })
public class CustomerTrackingController extends BaseController {

    private final CustomerTrackingService service;

    public CustomerTrackingController(
        CustomerTrackingService service,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.MY_BOOKINGS + Routes.TRIP_TRACKING)
    @Operation(
        summary = "Xem vị trí cho đơn của chính khách",
        description = "MY_BOOKING_READ/CUSTOMER ready và owner; chỉ CONFIRMED còn hiệu lực có vị trí, đơn hủy/hoàn tất/chờ chỉ UNAVAILABLE, không public GPS"
    )
    public ApiResult<TrackingResponse> read(
        @Parameter(description = "ID đơn dương, không phải ID chuyến") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.read(id, actor));
    }
}
