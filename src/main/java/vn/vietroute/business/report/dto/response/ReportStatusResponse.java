package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tập trạng thái hữu hạn, không phải danh sách đơn hoặc chuyến cần phân trang. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReportStatusResponse {

    @Schema(description = "Mã trạng thái")
    private String status;

    @Schema(description = "Số bản ghi")
    private long count;
}
