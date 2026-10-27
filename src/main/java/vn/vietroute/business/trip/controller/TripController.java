package vn.vietroute.business.trip.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.trip.dto.request.TripAssignmentRequest;
import vn.vietroute.business.trip.dto.request.TripChoiceRequest;
import vn.vietroute.business.trip.dto.request.TripCreateRequest;
import vn.vietroute.business.trip.dto.request.TripNoticeRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.request.TripStateRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.service.TripService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API staff chuyến; assignment và state tách để không nhận status/scope/giá tùy ý từ form. */
@RestController
@AccessFunction(group = Groups.TRIP, function = Functions.TRIP)
public class TripController extends BaseController {

    private final TripService service;

    public TripController(TripService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @PutMapping(Routes.ADMIN_TRIPS + Routes.TRIP_NOTICE)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Đăng thông báo vận hành chuyến",
        description = "OPEN/IN_PROGRESS, version bắt buộc; giữ trạng thái, ghi NOTICE. Không tự hoàn tiền/chuyển khách"
    )
    public ApiResult<TripResponse> notice(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TripNoticeRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.notice(id, body, actor));
    }

    @GetMapping(Routes.ADMIN_TRIPS)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.READ })
    @Operation(
        summary = "Tra chuyến theo scope",
        description = "Lọc query/carrierId/status/date/page/size ở PostgreSQL; ngày xuất phát Việt Nam"
    )
    public ApiResult<PageResponse<TripResponse>> page(
        @Valid @ModelAttribute TripSearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(query));
    }

    @GetMapping(Routes.ADMIN_TRIPS + Routes.BY_ID)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.READ })
    @Operation(summary = "Chi tiết chuyến và snapshot điểm", description = "READ độc lập VIEW; ID ngoài scope trả 404")
    public ApiResult<TripResponse> find(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @GetMapping(Routes.ADMIN_TRIPS + Routes.TRIP_CHOICES)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.CREATE, PermissionAction.UPDATE })
    @Operation(
        summary = "Chọn nhà xe/tuyến/xe/tài xế của chuyến",
        description = "tripId=0 kiểm CREATE; tripId dương kiểm UPDATE và ép nhà xe của chuyến. Không yêu cầu quyền CRUD tài nguyên phụ thuộc"
    )
    public ApiResult<PageResponse<TripChoiceResponse>> choices(
        @Parameter(description = "0 để tạo mới, ID chuyến dương để phân công lại") @RequestParam(
            defaultValue = "0"
        ) @Min(value = 0, message = "{validation.invalid}") long tripId,
        @Valid @ModelAttribute TripChoiceRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.choices(tripId, query));
    }

    @PostMapping(Routes.ADMIN_TRIPS)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Tạo chuyến nháp từ tuyến",
        description = "Chụp điểm/giờ/giá, tạo segment; xe và DRIVER ready cùng nhà xe; không tự mở bán"
    )
    public ApiResult<TripResponse> create(
        @Valid @RequestBody TripCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_TRIPS + Routes.TRIP_ASSIGNMENT)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Đổi phân công và số chỗ",
        description = "DRAFT/OPEN, version bắt buộc; chỉ DRAFT đổi giờ, kiểm chồng lịch và chỗ đã xác nhận"
    )
    public ApiResult<TripResponse> assignment(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TripAssignmentRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.assignment(id, body, actor));
    }

    @PutMapping(Routes.ADMIN_TRIPS + Routes.TRIP_STATE)
    @AccessFunction(group = Groups.TRIP, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Mở bán hoặc hủy chuyến trước khi chạy",
        description = "DRAFT→OPEN; DRAFT/OPEN→CANCELLED, hủy bắt buộc lý do. Backend khóa trip và kiểm version/lịch. Hủy đồng bộ toàn bộ đơn active và trả chỗ CONFIRMED theo chặng; thiếu lịch sử bất kỳ sẽ rollback toàn bộ."
    )
    public ApiResult<TripResponse> state(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody TripStateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.state(id, body, actor));
    }
}
