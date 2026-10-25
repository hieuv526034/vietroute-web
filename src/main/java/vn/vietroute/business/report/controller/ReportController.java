package vn.vietroute.business.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.business.report.dto.request.ReportCarrierSearchRequest;
import vn.vietroute.business.report.dto.request.ReportSearchRequest;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.service.ReportService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP báo cáo không xuất chi tiết hành khách hoặc credential; quyền READ độc lập VIEW. */
@RestController
@AccessFunction(group = Groups.REPORT, function = Functions.REPORT, actions = { PermissionAction.READ })
public class ReportController extends BaseController {

    private final ReportService service;

    public ReportController(ReportService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_REPORTS)
    @Operation(
        summary = "Tổng hợp chuyến và đơn trong phạm vi",
        description = "REPORT_READ scoped; fromDate/toDate theo ngày khởi hành Việt Nam, tối đa 366 ngày. Tiền là số hiện hành của đơn thuộc nhóm chuyến, không phải cashflow theo ngày thu."
    )
    public ApiResult<ReportSummaryResponse> summary(
        @Valid @ModelAttribute ReportSearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.summary(query));
    }

    @GetMapping(Routes.ADMIN_REPORT_CARRIERS)
    @Operation(
        summary = "Chọn nhà xe báo cáo",
        description = "REPORT_READ scoped; query/page/size lọc trước COUNT/LIMIT, không phụ thuộc CARRIER_READ"
    )
    public ApiResult<PageResponse<ReportCarrierResponse>> carriers(
        @Valid @ModelAttribute ReportCarrierSearchRequest query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.carriers(query));
    }
}
