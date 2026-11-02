package vn.vietroute.business.booking.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.dto.search.BookingSearch;
import vn.vietroute.business.booking.dto.search.BookingTripSearch;
import vn.vietroute.business.booking.service.BookingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.validation.RequestGroups;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Facade HTTP đơn staff theo scope; không nhận owner/source/giá hoặc actor client. */
@RestController
public class BookingAPI extends BaseController {

    private final BookingService service;

    public BookingAPI(BookingService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_TRIPS)
    @Operation(
        summary = "Chọn chuyến nhận đặt PHONE",
        description = "BOOKING_CREATE scoped, query/page/size ở DB; không cần grant GET danh sách chuyến riêng"
    )
    public ApiResult<PageResponse<BookingTripChoiceResponse>> choices(
        @Valid @ModelAttribute BookingTripSearch query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.choices(query));
    }

    @GetMapping(Routes.ADMIN_BOOKINGS)
    @Operation(
        summary = "Tra đơn trong phạm vi nhà xe",
        description = "query/tripId/status/page/size lọc trước COUNT/LIMIT ở PostgreSQL; không nhận customerId"
    )
    public ApiResult<PageResponse<BookingResponse>> page(
        @Valid @ModelAttribute BookingSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(search));
    }

    @GetMapping(Routes.ADMIN_BOOKINGS + Routes.BY_ID)
    @Operation(
        summary = "Chi tiết đơn và lịch sử",
        description = "ID ngoài scope trả 404; GET dữ liệu độc lập GET menu"
    )
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
    @Operation(
        summary = "Gửi yêu cầu đặt PHONE",
        description = "PENDING không giữ chỗ, chụp giá theo cặp stop của trip, kiểm số chỗ mọi đoạn và UUID chống tạo trùng"
    )
    public ApiResult<BookingResponse> create(
        @Validated(RequestGroups.Create.class) @RequestBody BookingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_STATE)
    @Operation(
        summary = "Xác nhận, từ chối hoặc hủy đơn",
        description = "Khóa trip → booking → segments; version bắt buộc, history và số chỗ cùng transaction"
    )
    public ApiResult<BookingResponse> state(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.State.class) @RequestBody BookingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.state(id, body, actor));
    }

    @PutMapping(Routes.ADMIN_BOOKINGS + Routes.BOOKING_PAYMENT)
    @Operation(
        summary = "Ghi nhận thu hoặc hoàn trực tiếp",
        description = "Chỉ ghi tiền đã thực hiện ngoài hệ thống; giới hạn theo giá/đã thu, UUID chống lặp và history nguyên tử"
    )
    public ApiResult<BookingResponse> payment(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.Payment.class) @RequestBody BookingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.payment(id, body, actor));
    }

    @GetMapping(Routes.MY_BOOKINGS)
    @Operation(
        summary = "Tra đơn của chính khách hàng",
        description = "query/tripId/status/page/size lọc trước COUNT/LIMIT ở PostgreSQL; không nhận customerId"
    )
    public ApiResult<PageResponse<BookingResponse>> customerPage(
        @Valid @ModelAttribute BookingSearch search,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.customerPage(search, actor));
    }

    @GetMapping(Routes.MY_BOOKINGS + Routes.BY_ID)
    @Operation(
        summary = "Chi tiết đơn và lịch sử",
        description = "ID ngoài owner trả 404; GET dữ liệu độc lập GET menu"
    )
    public ApiResult<BookingResponse> customerFind(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.customerFind(id, actor));
    }

    @PostMapping(Routes.MY_BOOKINGS)
    @Operation(
        summary = "Gửi yêu cầu đặt WEB",
        description = "PENDING không giữ chỗ, chụp giá theo cặp stop của trip, kiểm số chỗ mọi đoạn và UUID chống tạo trùng"
    )
    public ApiResult<BookingResponse> customerCreate(
        @Validated(RequestGroups.Create.class) @RequestBody BookingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.customerCreate(body, actor));
    }

    @PutMapping(Routes.MY_BOOKINGS + Routes.BOOKING_STATE)
    @Operation(
        summary = "Hủy đơn của chính khách hàng",
        description = "Khóa trip → booking → segments; version bắt buộc, history và số chỗ cùng transaction"
    )
    public ApiResult<BookingResponse> customerState(
        @Parameter(description = "ID đơn dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Validated(RequestGroups.State.class) @RequestBody BookingRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.customerState(id, body, actor));
    }
}
