package vn.vietroute.business.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.report.repo.ReportRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Báo cáo chỉ đọc, guard chức năng và scope cùng connection; không phụ thuộc quyền CRUD các module. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepo reports;
    private final CarrierRepo carriers;

    /** Picker không mở toàn bộ hồ sơ nhà xe hoặc union quyền từ phạm vi khác. */
    public PageResponse<ReportCarrierResponse> carriers(ReportCarrierSearch query) {
        return reports.carriers(query);
    }

    /** Xác thực ID trước tổng hợp; ID ngoài scope trả 404. Dữ liệu trạng thái/tiền cùng snapshot statement. */
    public ReportSummaryResponse summary(ReportSearch query) {
        BusinessRules.require(
            query.isDateRangeValid() && (query.getCarrierId() == null || query.getCarrierId() > 0),
            ErrorCode.INVALID_REQUEST
        );
        if (query.getCarrierId() != null) carriers.carrier(query.getCarrierId(), false);
        return reports.summary(query);
    }
}
