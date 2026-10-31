package vn.vietroute.auth.dto.request.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Bộ lọc danh bạ thực hiện tại DB trước phân trang; từ khóa được bind nguyên văn, không phải SQL/wildcard. */
@Getter
@Setter
@Schema(description = "Lọc danh bạ tài nguyên UI/API đã được startup đồng bộ")
public class AccessResourceSearchRequest extends PageRequest {

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = Regex.ACCESS_RESOURCE_KIND, message = "{validation.invalid}")
    @Schema(
        description = "Loại tài nguyên; rỗng để xem tất cả",
        allowableValues = { "", "MENU", "ENDPOINT" },
        defaultValue = ""
    )
    private String kind = "";

    @NotNull(message = "{validation.required}")
    @Size(max = 160, message = "{validation.resourceSearch}")
    @Pattern(regexp = Regex.ACCESS_RESOURCE_SEARCH, message = "{validation.resourceSearch}")
    @Schema(
        description = "Tìm không phân biệt hoa/thường trong route, controller, mô tả, nhóm, chức năng và mã quyền; %, _ là ký tự thường",
        maxLength = 160,
        defaultValue = "",
        example = "catalog"
    )
    private String query = "";
}
