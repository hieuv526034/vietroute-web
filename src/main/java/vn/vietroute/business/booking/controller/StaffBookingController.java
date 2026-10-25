package vn.vietroute.business.booking.controller;

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
import vn.vietroute.business.booking.dto.request.BookingCreateRequest;
import vn.vietroute.business.booking.dto.request.BookingPaymentRequest;
import vn.vietroute.business.booking.dto.request.BookingSearchRequest;
import vn.vietroute.business.booking.dto.request.BookingStateRequest;
import vn.vietroute.business.booking.dto.request.BookingTripSearchRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.service.StaffBookingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Facade HTTP đơn staff theo scope; không nhận owner/source/giá hoặc actor client. */
@RestController
@AccessFunction(group = Groups.BOOKING, function = Functions.BOOKING)
public class StaffBookingController extends BaseController {

    private final StaffBookingService service;

    public StaffBookingController(StaffBookingService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_TRIPS)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Chọn chuyến nhận đặt PHONE",
        description = "BOOKING_CREATE scoped, query/page/size ở DB; không cần TRIP_READ"
    )
    public ApiResult<PageResponse<BookingTripChoiceResponse>> choices(
        @Valid @ModelAttribute BookingTripSearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.choices(query));
    }

    @GetMapping(Routes.ADMIN_BOOKINGS)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.READ })
    @Operation(
        summary = "Tra đơn trong phạm vi nhà xe",
        description = "query/tripId/status/page/size lọc trước COUNT/LIMIT ở PostgreSQL; không nhận customerId"
    )
    public ApiResult<PageResponse<BookingResponse>> page(
        @Valid @ModelAttribute BookingSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(search));
    }

    @GetMapping(Routes.ADMIN_BOOKINGS + Routes.BY_ID)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.READ })
    @Operation(summary = "Chi tiết đơn và lịch sử", description = "ID ngoài scope trả 404; READ độc lập VIEW")
    public ApiResult<BookingResponse> find(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @PostMapping(Routes.ADMIN_BOOKINGS)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Gửi yêu cầu đặt PHONE",
        description = "PENDING không giữ chỗ, chụp giá theo cặp stop của trip, kiểm số chỗ mọi đoạn và UUID chống tạo trùng"
    )
    public ApiResult<BookingResponse> create(
        @Valid @RequestBody BookingCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Xác nhận, từ chối hoặc hủy đơn",
        description = "Khóa trip → booking → segments; version bắt buộc, history và số chỗ cùng transaction"
    )
    public ApiResult<BookingResponse> state(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody BookingStateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.state(id, body, actor));
    }

    @PutMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_PAYMENT)
    @AccessFunction(group = Groups.BOOKING, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Ghi nhận thu hoặc hoàn trực tiếp",
        description = "Chỉ ghi tiền đã thực hiện ngoài hệ thống; giới hạn theo giá/đã thu, UUID chống lặp và history nguyên tử"
    )
    public ApiResult<BookingResponse> payment(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody BookingPaymentRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.payment(id, body, actor));
    }
}
