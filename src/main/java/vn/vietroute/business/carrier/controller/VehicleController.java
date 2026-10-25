package vn.vietroute.business.carrier.controller;

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
import vn.vietroute.business.carrier.dto.request.VehicleCreateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.VehicleResponse;
import vn.vietroute.business.carrier.service.VehicleService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP xe chỉ bind trường cho phép; service/DAO kiểm quyền và scope hiện hành độc lập. */
@RestController
@AccessFunction(group = Groups.VEHICLE, function = Functions.VEHICLE)
public class VehicleController extends BaseController {

    @GetMapping(Routes.ADMIN_VEHICLES + Routes.CARRIER_CHOICES)
    @AccessFunction(group = Groups.VEHICLE, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Chọn nhà xe để tạo xe",
        description = "Projection ID/mã/tên của nhà xe active trong scope có CREATE; tìm/phân trang server."
    )
    public ApiResult<PageResponse<CarrierChoiceResponse>> carriers(
        @Valid @ModelAttribute CarrierSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.carriers(page));
    }

    private final VehicleService service;

    public VehicleController(VehicleService service, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_VEHICLES)
    @AccessFunction(group = Groups.VEHICLE, actions = { PermissionAction.READ })
    @Operation(
        summary = "Danh sách xe theo phạm vi",
        description = "Tìm/lọc trạng thái/phân trang trên PostgreSQL; count và bản ghi cùng điều kiện scope. Không lấy quyền từ carrierId client."
    )
    public ApiResult<PageResponse<VehicleResponse>> page(
        @Valid @ModelAttribute CarrierSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(page));
    }

    @GetMapping(Routes.ADMIN_VEHICLES + Routes.BY_ID)
    @AccessFunction(group = Groups.VEHICLE, actions = { PermissionAction.READ })
    @Operation(
        summary = "Chi tiết xe",
        description = "ID không tồn tại hoặc ngoài phạm vi trả 404, thiếu quyền chức năng trả 403."
    )
    public ApiResult<VehicleResponse> find(
        @Parameter(description = "ID xe dương") @PathVariable @Min(value = 1, message = "{validation.invalid}") long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @PostMapping(Routes.ADMIN_VEHICLES)
    @AccessFunction(group = Groups.VEHICLE, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Tạo xe",
        description = "Actor/scope/audit do server xác định. Cha phải active và thuộc phạm vi; FK và UNIQUE chốt dữ liệu."
    )
    public ApiResult<VehicleResponse> create(
        @Valid @RequestBody VehicleCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_VEHICLES + Routes.BY_ID)
    @AccessFunction(group = Groups.VEHICLE, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Sửa/ngừng sử dụng xe",
        description = "Giữ code/carrier/scope, không xóa lịch sử. Version cũ trả 409, không tự gửi lại. "
    )
    public ApiResult<VehicleResponse> update(
        @Parameter(description = "ID xe dương") @PathVariable @Min(value = 1, message = "{validation.invalid}") long id,
        @Valid @RequestBody VehicleUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.update(id, body, actor));
    }
}
