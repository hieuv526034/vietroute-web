package vn.vietroute.business.journey.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.journey.dto.request.JourneyQuoteRequest;
import vn.vietroute.business.journey.dto.request.JourneySearchRequest;
import vn.vietroute.business.journey.dto.response.JourneyMatchResponse;
import vn.vietroute.business.journey.dto.response.JourneyQuoteResponse;
import vn.vietroute.business.journey.dto.response.JourneyResponse;
import vn.vietroute.business.journey.service.JourneyService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** GET công khai được WebRoutePolicy khai báo tường minh; các mutation BOOKING vẫn bảo vệ riêng. */
@RestController
@AccessFunction(group = Groups.TRIP, function = Functions.TRIP)
public class JourneyController extends BaseController {

    private final JourneyService service;

    public JourneyController(JourneyService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.PUBLIC_TRIPS + Routes.JOURNEY_QUOTE)
    @AccessFunction(group = Groups.TRIP)
    @Operation(
        summary = "Báo giá/chỗ theo cặp điểm chuyến",
        description = "GET không giữ chỗ, pickupStopId/dropoffStopId/seats, policy/chỗ kiểm lại khi tạo và xác nhận; không nhận giá client"
    )
    public ApiResult<JourneyQuoteResponse> quote(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @ModelAttribute JourneyQuoteRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.quote(id, query));
    }

    @GetMapping(Routes.PUBLIC_TRIPS)
    @AccessFunction(group = Groups.TRIP)
    @Operation(
        summary = "Tìm chặng chuyến công khai",
        description = "Ngày tại pickup Việt Nam, hướng tăng, chưa rời điểm đón, MIN chỗ toàn range. OPEN chưa quá giờ hoặc IN_PROGRESS, nhà xe/scope active"
    )
    public ApiResult<PageResponse<JourneyMatchResponse>> search(
        @Valid @ModelAttribute JourneySearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.search(query));
    }

    @GetMapping(Routes.PUBLIC_TRIPS + Routes.BY_ID)
    @AccessFunction(group = Groups.TRIP)
    @Operation(
        summary = "Chi tiết chuyến đã công bố và tiến độ",
        description = "Projection không có account/driver phone/audit/hành khách. Không xuất DRAFT; trả snapshot và chỗ còn từng đoạn tại lúc đọc"
    )
    public ApiResult<JourneyResponse> find(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }
}
