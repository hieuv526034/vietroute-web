package vn.vietroute.business.tracking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.tracking.dto.request.TrackingSampleRequest;
import vn.vietroute.business.tracking.dto.request.TrackingStartRequest;
import vn.vietroute.business.tracking.dto.request.TrackingStopRequest;
import vn.vietroute.business.tracking.dto.response.TrackingPointResponse;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.dto.response.TrackingSessionResponse;
import vn.vietroute.business.tracking.service.DriverTrackingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Route riêng tư, không đặt GPS dưới matcher public /trips/**. */
@RestController
@AccessFunction(group = Groups.DRIVER_TRIP, function = Functions.DRIVER_TRIP)
public class DriverTrackingController extends BaseController {

    private final DriverTrackingService service;

    public DriverTrackingController(DriverTrackingService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.READ })
    @Operation(
        summary = "Xem vị trí chuyến được giao",
        description = "Không có ADMIN bypass; không trả consent nonce, trạng thái stale/lease độc lập tiến độ chặng"
    )
    public ApiResult<TrackingResponse> read(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.read(id, actor));
    }

    @PostMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING_START)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Bật chia sẻ vị trí có consent",
        description = "Tài xế chủ động; version chuyến, mới trước khởi hành 30 phút hoặc đang chạy; nonce mới thu hồi nonce trước"
    )
    public ApiResult<TrackingSessionResponse> start(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TrackingStartRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.start(id, body, actor));
    }

    @PostMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING_STOP)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Dừng chia sẻ và xóa snapshot tọa độ",
        description = "Cùng nonce lặp lại an toàn; nonce cũ không dừng consent mới; vẫn dừng được khi feature tắt"
    )
    public ApiResult<TrackingResponse> stop(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TrackingStopRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.stop(id, body, actor));
    }

    @PostMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING_SAMPLES)
    @AccessFunction(group = Groups.DRIVER_TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Gửi mẫu thiết bị trong consent hiện hành",
        description = "WGS84/accuracy và measuredAt độ phân giải millisecond; không quá cũ/future/sai thứ tự; tối thiểu 10 giây, replay không gia hạn lease. Source/actor/receivedAt do server."
    )
    public ApiResult<TrackingPointResponse> sample(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TrackingSampleRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.sample(id, body, actor));
    }
}
