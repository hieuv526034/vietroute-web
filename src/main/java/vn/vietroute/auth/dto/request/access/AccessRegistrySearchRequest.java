package vn.vietroute.auth.dto.request.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Tìm mã/tên vi/en trong PostgreSQL trước phân trang, không lọc riêng trang đang nhìn thấy. */
@Getter
@Setter
@Schema(description = "Tra registry role/quyền theo mã, tên và trạng thái")
public class AccessRegistrySearchRequest extends PageRequest {

    @NotNull(message = "{validation.required}")
    @Size(max = 160, message = "{validation.resourceSearch}")
    @Pattern(regexp = Regex.ACCESS_RESOURCE_SEARCH, message = "{validation.resourceSearch}")
    @Schema(
        description = "Từ khóa nguyên văn trong mã/tên vi/en, không phân biệt hoa thường",
        maxLength = 160,
        defaultValue = ""
    )
    private String query = "";

    @Schema(description = "Lọc mã bật/tắt; bỏ trống để lấy cả hai")
    private Boolean active;
}
