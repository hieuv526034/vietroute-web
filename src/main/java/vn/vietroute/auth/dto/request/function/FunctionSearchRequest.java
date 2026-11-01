package vn.vietroute.auth.dto.request.function;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Bộ lọc SQL trước count/page; không chấp nhận tên cột SQL từ client. */
@Getter
@Setter
@Schema(description = "Tìm mã/tên chức năng và lọc trạng thái, phân trang server")
public class FunctionSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.invalid}")
    @Schema(description = "Chuỗi nguyên văn trong mã hoặc tên vi/en")
    private String query = "";

    @Schema(description = "Lọc đang bật/tắt, bỏ qua để lấy cả hai")
    private Boolean active;
}
