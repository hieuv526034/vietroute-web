package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Quản trị địa điểm; không bị lọc mất dữ liệu do tỉnh/thành đã ngừng sử dụng")
public class LocationSearchRequest extends LocationLookupRequest {

    @Schema(description = "Lọc trạng thái riêng của địa điểm, bỏ trống lấy cả hai")
    private Boolean active;
}
