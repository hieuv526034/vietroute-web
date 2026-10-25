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
import vn.vietroute.business.carrier.dto.request.CarrierCreateRequest;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.CarrierUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.service.CarrierService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP nhà xe chỉ bind trường cho phép; service/DAO kiểm quyền và scope hiện hành độc lập. */
@RestController
@AccessFunction(group = Groups.CARRIER, function = Functions.CARRIER)
public class CarrierController extends BaseController {

    private final CarrierService service;

    public CarrierController(CarrierService service, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_CARRIERS)
    @AccessFunction(group = Groups.CARRIER, actions = { PermissionAction.READ })
    @Operation(
        summary = "Danh sách nhà xe theo phạm vi",
        description = "Tìm/lọc trạng thái/phân trang trên PostgreSQL; count và bản ghi cùng điều kiện scope. Không lấy quyền từ carrierId client."
    )
    public ApiResult<PageResponse<CarrierResponse>> page(
        @Valid @ModelAttribute CarrierSearchRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(page));
    }

    @GetMapping(Routes.ADMIN_CARRIERS + Routes.BY_ID)
    @AccessFunction(group = Groups.CARRIER, actions = { PermissionAction.READ })
    @Operation(
        summary = "Chi tiết nhà xe",
        description = "ID không tồn tại hoặc ngoài phạm vi trả 404, thiếu quyền chức năng trả 403."
    )
    public ApiResult<CarrierResponse> find(
        @Parameter(description = "ID nhà xe dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }

    @PostMapping(Routes.ADMIN_CARRIERS)
    @AccessFunction(group = Groups.CARRIER, actions = { PermissionAction.CREATE }, adminOnly = true)
    @Operation(
        summary = "Tạo nhà xe",
        description = "Actor/scope/audit do server xác định. Chỉ ADMIN; scope và nhà xe tạo nguyên tử."
    )
    public ApiResult<CarrierResponse> create(
        @Valid @RequestBody CarrierCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> service.create(body, actor));
    }

    @PutMapping(Routes.ADMIN_CARRIERS + Routes.BY_ID)
    @AccessFunction(group = Groups.CARRIER, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Sửa/ngừng sử dụng nhà xe",
        description = "Giữ code/carrier/scope, không xóa lịch sử. Version cũ trả 409, không tự gửi lại. Chỉ ADMIN được đổi trạng thái nhà xe."
    )
    public ApiResult<CarrierResponse> update(
        @Parameter(description = "ID nhà xe dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        @Valid @RequestBody CarrierUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.update(id, body, actor));
    }
}
