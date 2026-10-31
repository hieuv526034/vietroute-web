package vn.vietroute.auth.dto.request.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Lọc danh tính quản trị bằng tham số bind JDBC; không nhận tên cột hoặc SQL từ client. */
@Getter
@Setter
@Schema(description = "Lọc tài khoản quản trị, loại trừ actor system")
public class ManagedAccountSearchRequest extends AccessUserSearchRequest {

    @Schema(description = "Lọc theo role đã gán; bỏ trống để xem tất cả", example = "DRIVER")
    @Pattern(regexp = Regex.ROLE_SEARCH, message = "{validation.invalid}")
    private String role;

    @Schema(description = "Trạng thái tài khoản; bỏ trống để lấy cả bật/tắt")
    private Boolean active;
}
