package vn.vietroute.business.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.report.repo.ReportRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Báo cáo chỉ đọc, guard chức năng và scope cùng connection; không phụ thuộc quyền CRUD các module. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepo reports;
    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;

    /** Picker không mở toàn bộ hồ sơ nhà xe hoặc union quyền từ phạm vi khác. */
    public PageResponse<ReportCarrierResponse> carriers(ReportCarrierSearch query) {
        return transactions.execute(connection -> reports.carriers(connection, query));
    }

    /** Xác thực ID trước tổng hợp; ID ngoài scope trả 404. Dữ liệu trạng thái/tiền cùng snapshot statement. */
    public ReportSummaryResponse summary(ReportSearch query) {
        BusinessRules.require(
            query.isDateRangeValid() && (query.getCarrierId() == null || query.getCarrierId() > 0),
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            if (query.getCarrierId() != null) carriers.carrier(connection, query.getCarrierId(), false);
            return reports.summary(connection, query);
        });
    }
}
