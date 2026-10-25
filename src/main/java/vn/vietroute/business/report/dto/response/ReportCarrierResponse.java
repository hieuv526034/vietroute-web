package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Không xuất nhân sự, scope, liên hệ hoặc audit qua picker báo cáo. */
public record ReportCarrierResponse(
    @Schema(description = "ID nhà xe được phép") long id,
    @Schema(description = "Mã nhà xe") String code,
    @Schema(description = "Tên nhà xe") String name,
    @Schema(description = "Trạng thái kinh doanh; nhà xe tắt vẫn có dữ liệu lịch sử") boolean active
) {}
