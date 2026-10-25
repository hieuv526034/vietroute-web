package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tập trạng thái hữu hạn, không phải danh sách đơn hoặc chuyến cần phân trang. */
public record ReportStatusResponse(
    @Schema(description = "Mã trạng thái") String status,
    @Schema(description = "Số bản ghi") long count
) {}
