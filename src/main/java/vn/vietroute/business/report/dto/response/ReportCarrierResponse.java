package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Không xuất nhân sự, scope, liên hệ hoặc audit qua picker báo cáo. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReportCarrierResponse {

    @Schema(description = "ID nhà xe được phép")
    private long id;

    @Schema(description = "Mã nhà xe")
    private String code;

    @Schema(description = "Tên nhà xe")
    private String name;

    @Schema(description = "Trạng thái kinh doanh; nhà xe tắt vẫn có dữ liệu lịch sử")
    private boolean active;
}
