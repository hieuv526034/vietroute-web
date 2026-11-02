package vn.vietroute.auth.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Query tìm kiếm/phân trang, không chứa dữ liệu ghi hoặc actor. */
@Getter
@Setter
public class FunctionSearch extends PageRequest {

    @Schema(description = "Bộ lọc query; để trống để không lọc")
    @Size(max = 160)
    private String query = "";

    @Schema(description = "Bộ lọc active; để trống để không lọc")
    private Boolean active = null;

    @Schema(description = "Bộ lọc kind; để trống để không lọc")
    @Size(max = 160)
    private String kind = "";
}
