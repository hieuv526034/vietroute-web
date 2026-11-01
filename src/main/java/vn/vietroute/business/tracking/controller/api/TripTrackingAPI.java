package vn.vietroute.business.tracking.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.tracking.dto.request.TrackingRequest;
import vn.vietroute.business.tracking.dto.response.TrackingPointResponse;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.dto.response.TrackingSessionResponse;
import vn.vietroute.business.tracking.service.TrackingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.validation.RequestGroups;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Staff không có endpoint ghi vị trí thay tài xế. */
@RestController
public class TripTrackingAPI extends BaseController {

    private final TrackingService service;

    public TripTrackingAPI(TrackingService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_TRIPS + Routes.TRIP_TRACKING)
    @Operation(
        summary = "Xem vị trí trong nhà xe",
        description = "Grant GET endpoint chuyến trong scope, ID ngoài scope404; chỉ consent còn lease, không trả nonce hoặc actor"
    )
    public ApiResult<TrackingResponse> readStaff(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.readStaff(id));
    }

    @GetMapping(Routes.MY_BOOKINGS + Routes.TRIP_TRACKING)
    @Operation(
        summary = "Xem vị trí cho đơn của chính khách",
        description = "MY_BOOKING_READ/CUSTOMER ready và owner; chỉ CONFIRMED còn hiệu lực có vị trí, đơn hủy/hoàn tất/chờ chỉ UNAVAILABLE, không public GPS"
    )
    public ApiResult<TrackingResponse> readCustomer(
        @Parameter(description = "ID đơn dương, không phải ID chuyến") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.readCustomer(id, actor));
    }

    @GetMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING)
    @Operation(
        summary = "Xem vị trí chuyến được giao",
        description = "Không có ADMIN bypass; không trả consent nonce, trạng thái stale/lease độc lập tiến độ chặng"
    )
    public ApiResult<TrackingResponse> readDriver(
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
    @Operation(
        summary = "Bật chia sẻ vị trí có consent",
        description = "Tài xế chủ động; version chuyến, mới trước khởi hành 30 phút hoặc đang chạy; nonce mới thu hồi nonce trước"
    )
    public ApiResult<TrackingSessionResponse> start(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Create.class) @RequestBody TrackingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.start(id, body, actor));
    }

    @PostMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING_STOP)
    @Operation(
        summary = "Dừng chia sẻ và xóa snapshot tọa độ",
        description = "Cùng nonce lặp lại an toàn; nonce cũ không dừng consent mới; vẫn dừng được khi feature tắt"
    )
    public ApiResult<TrackingResponse> stop(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Delete.class) @RequestBody TrackingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.stop(id, body, actor));
    }

    @PostMapping(Routes.DRIVER_TRIPS + Routes.TRIP_TRACKING_SAMPLES)
    @Operation(
        summary = "Gửi mẫu thiết bị trong consent hiện hành",
        description = "WGS84/accuracy và measuredAt độ phân giải millisecond; không quá cũ/future/sai thứ tự; tối thiểu 10 giây, replay không gia hạn lease. Source/actor/receivedAt do server."
    )
    public ApiResult<TrackingPointResponse> sample(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Update.class) @RequestBody TrackingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.sample(id, body, actor));
    }
}
