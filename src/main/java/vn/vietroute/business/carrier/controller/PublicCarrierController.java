package vn.vietroute.business.carrier.controller;

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
import vn.vietroute.business.carrier.dto.request.PublicCarrierSearchRequest;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.service.PublicCarrierService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** GET công khai được route policy cho phép; không chia sẻ DTO/matcher với API quản trị. */
@RestController
@AccessFunction(group = Groups.CARRIER, function = Functions.CARRIER)
public class PublicCarrierController extends BaseController {

    private final PublicCarrierService service;

    public PublicCarrierController(PublicCarrierService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.PUBLIC_CARRIERS)
    @Operation(
        summary = "Tra cứu nhà xe công khai",
        description = "Chỉ nhà xe/scope active, query/page/size tại DB; không scope/nhân sự/audit"
    )
    public ApiResult<PageResponse<PublicCarrierResponse>> page(
        @Valid @ModelAttribute PublicCarrierSearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(query));
    }

    @GetMapping(Routes.PUBLIC_CARRIERS + Routes.BY_ID)
    @Operation(
        summary = "Thông tin và liên hệ nhà xe công khai",
        description = "Nhà xe/scope ngừng công bố trả 404; logo READY từ media chung, không có metadata thư viện"
    )
    public ApiResult<PublicCarrierResponse> find(
        @Parameter(description = "ID nhà xe dương") @PathVariable @Min(
            value = 1,
            message = "{validation.invalid}"
        ) long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.find(id));
    }
}
