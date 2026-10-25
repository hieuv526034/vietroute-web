package vn.vietroute.business.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.report.dto.request.ReportCarrierSearchRequest;
import vn.vietroute.business.report.dto.request.ReportSearchRequest;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.repository.ReportRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Báo cáo chỉ đọc, guard chức năng và scope cùng connection; không phụ thuộc quyền CRUD các module. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.REPORT)
public class ReportService {

    private final ReportRepository reports;
    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;

    /** Picker không mở toàn bộ hồ sơ nhà xe hoặc union quyền từ phạm vi khác. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<ReportCarrierResponse> carriers(ReportCarrierSearchRequest query) {
        return transactions.execute(connection -> reports.carriers(connection, query));
    }

    /** Xác thực ID trước tổng hợp; ID ngoài scope trả 404. Dữ liệu trạng thái/tiền cùng snapshot statement. */
    @AccessOperation(action = PermissionAction.READ)
    public ReportSummaryResponse summary(ReportSearchRequest query) {
        BusinessRules.require(
            query.isDateRangeValid() && (query.getCarrierId() == null || query.getCarrierId() > 0),
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            if (query.getCarrierId() != null) carriers.carrier(
                connection,
                query.getCarrierId(),
                Groups.REPORT,
                PermissionAction.READ,
                false
            );
            return reports.summary(connection, query);
        });
    }
}
