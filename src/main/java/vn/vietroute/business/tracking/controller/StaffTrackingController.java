package vn.vietroute.business.tracking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.service.StaffTrackingService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Staff không có endpoint ghi vị trí thay tài xế. */
@RestController
@AccessFunction(group = Groups.TRIP, function = Functions.TRIP, actions = { PermissionAction.READ })
public class StaffTrackingController extends BaseController {

    private final StaffTrackingService service;

    public StaffTrackingController(StaffTrackingService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_TRIPS + Routes.TRIP_TRACKING)
    @Operation(
        summary = "Xem vị trí trong nhà xe",
        description = "TRIP_READ scoped, ID ngoài scope404; chỉ consent còn lease, không trả nonce hoặc actor"
    )
    public ApiResult<TrackingResponse> read(
        @Parameter(description = "ID chuyến dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.read(id));
    }
}
