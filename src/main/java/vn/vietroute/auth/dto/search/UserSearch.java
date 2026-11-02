package vn.vietroute.auth.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Query tìm kiếm/phân trang, không chứa dữ liệu ghi hoặc actor. */
@Getter
@Setter
public class UserSearch extends PageRequest {

    @Schema(description = "Trạng thái tài khoản tùy chọn")
    private Boolean active;

    @Schema(description = "Bộ lọc username; để trống để không lọc")
    @Size(max = 160, message = "{validation.length}")
    private String username = "";

    @Schema(description = "Bộ lọc role; để trống để không lọc")
    @Size(max = 160)
    private String role = "";

    @Schema(description = "Bộ lọc userId; để trống để không lọc")
    private Long userId = null;
}
