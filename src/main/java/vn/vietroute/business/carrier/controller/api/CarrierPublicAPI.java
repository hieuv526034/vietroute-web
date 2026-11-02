package vn.vietroute.business.carrier.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.service.PublicCarrierService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** GET công khai được route policy cho phép; không chia sẻ DTO/matcher với API quản trị. */
@RestController
public class CarrierPublicAPI extends BaseController {

    private final PublicCarrierService service;

    public CarrierPublicAPI(PublicCarrierService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.PUBLIC_CARRIERS)
    @Operation(
        summary = "Tra cứu nhà xe công khai",
        description = "Chỉ nhà xe/scope active, query/page/size tại DB; không scope/nhân sự/audit"
    )
    public ApiResult<PageResponse<PublicCarrierResponse>> page(
        @Valid @ModelAttribute PublicCarrierSearch query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.page(query));
    }

    @GetMapping(Routes.PUBLIC_CARRIERS + Routes.BY_ID)
    @Operation(
        summary = "Thông tin và liên hệ nhà xe công khai",
        description = "Nhà xe/scope ngừng công bố trả 404; thông tin công bố, không có dữ liệu nội bộ"
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
