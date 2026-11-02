package vn.vietroute.business.report.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.report.service.ReportService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** HTTP báo cáo không xuất chi tiết hành khách hoặc credential; quyền GET dữ liệu độc lập GET menu. */
@RestController
public class ReportAPI extends BaseController {

    private final ReportService service;

    public ReportAPI(ReportService service, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.service = service;
    }

    @GetMapping(Routes.ADMIN_REPORTS)
    @Operation(
        summary = "Tổng hợp chuyến và đơn trong phạm vi",
        description = "REPORT_READ scoped; fromDate/toDate theo ngày khởi hành Việt Nam, tối đa 366 ngày. Tiền là số hiện hành của đơn thuộc nhóm chuyến, không phải cashflow theo ngày thu."
    )
    public ApiResult<ReportSummaryResponse> summary(
        @Valid @ModelAttribute ReportSearch query,
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
        @Valid @ModelAttribute ReportCarrierSearch query,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> service.carriers(query));
    }
}
