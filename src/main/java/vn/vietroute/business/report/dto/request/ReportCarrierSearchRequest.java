package vn.vietroute.business.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Picker chỉ xuất nhà xe có REPORT_READ, không phụ thuộc quyền CRUD nhà xe. */
@Getter
@Setter
public class ReportCarrierSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Từ khóa mã hoặc tên nhà xe, nguyên văn")
    private String query;
}
