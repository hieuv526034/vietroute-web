package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Quản trị tỉnh/thành, gồm cả bản ngừng sử dụng")
public class ProvinceSearchRequest extends ProvinceLookupRequest {

    @Schema(description = "Lọc đang/ngừng sử dụng; bỏ trống lấy cả hai")
    private Boolean active;
}
