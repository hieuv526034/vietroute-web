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
import vn.vietroute.auth.dto.record.account.DriverAccountResponse;
import vn.vietroute.auth.dto.request.account.DriverAccountCreateRequest;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.DriverCreateRequest;
import vn.vietroute.business.carrier.dto.request.DriverUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.DriverResponse;
import vn.vietroute.business.carrier.service.DriverService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP tài xế chỉ bind trường cho phép; service/DAO kiểm quyền và scope hiện hành độc lập. */
@RestController
@AccessFunction(group = Groups.DRIVER, function = Functions.DRIVER)
public class DriverController extends BaseController {

    @GetMapping(Routes.ADMIN_DRIVERS + Routes.CARRIER_CHOICES)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Chọn nhà xe để tạo tài xế",
        description = "Projection ID/mã/tên của nhà xe active trong scope có CREATE; tìm/phân trang server."
    )
    public ApiResult<PageResponse<CarrierChoiceResponse>> carriers(
        @Valid @ModelAttribute CarrierSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.carriers(page));
    }

    private final DriverService service;

    @PostMapping(Routes.ADMIN_DRIVERS + Routes.DRIVER_ACCOUNT)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.ASSIGN })
    @Operation(
        summary = "Cấp tài khoản DRIVER cho hồ sơ chưa liên kết",
        description = "DRIVER_ASSIGN scoped, version bắt buộc. Tạo account/membership/link/history nguyên tử; hồ sơ tạm ngừng, account phải đổi mật khẩu và xác minh email. Không nhận role/scope hoặc sửa account có sẵn."
    )
    public ApiResult<DriverAccountResponse> provision(
        @Parameter(description = "ID hồ sơ dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody DriverAccountCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.provision(id, body, actor));
    }

    public DriverController(DriverService service, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_DRIVERS)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.READ })
    @Operation(
        summary = "Danh sách tài xế theo phạm vi",
        description = "Tìm/lọc trạng thái/phân trang trên PostgreSQL; count và bản ghi cùng điều kiện scope. Không lấy quyền từ carrierId client."
    )
    public ApiResult<PageResponse<DriverResponse>> page(
        @Valid @ModelAttribute CarrierSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(page));
    }

    @GetMapping(Routes.ADMIN_DRIVERS + Routes.BY_ID)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.READ })
    @Operation(
        summary = "Chi tiết tài xế",
        description = "ID không tồn tại hoặc ngoài phạm vi trả 404, thiếu quyền chức năng trả 403."
    )
    public ApiResult<DriverResponse> find(
        @Parameter(description = "ID tài xế dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @PostMapping(Routes.ADMIN_DRIVERS)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Tạo tài xế",
        description = "Actor/scope/audit do server xác định. Cha phải active và thuộc phạm vi; FK và UNIQUE chốt dữ liệu."
    )
    public ApiResult<DriverResponse> create(
        @Valid @RequestBody DriverCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_DRIVERS + Routes.BY_ID)
    @AccessFunction(group = Groups.DRIVER, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Sửa/ngừng sử dụng tài xế",
        description = "Giữ code/carrier/scope, không xóa lịch sử. Version cũ trả 409, không tự gửi lại. "
    )
    public ApiResult<DriverResponse> update(
        @Parameter(description = "ID tài xế dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody DriverUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.update(id, body, actor));
    }
}
