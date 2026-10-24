package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

/** Lọc danh tính quản trị bằng tham số bind JDBC; không nhận tên cột hoặc SQL từ client. */
@Getter
@Setter
@Schema(description = "Lọc tài khoản quản trị, loại trừ actor system")
public class ManagedAccountSearchRequest extends AccessUserSearchRequest {

    @Schema(description = "Lọc theo role đã gán; bỏ trống để xem tất cả", example = "DRIVER")
    private Role role;

    @Schema(description = "Trạng thái tài khoản; bỏ trống để lấy cả bật/tắt")
    private Boolean active;
}
