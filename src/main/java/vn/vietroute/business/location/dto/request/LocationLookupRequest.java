package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Tra địa điểm, chỉ công bố địa điểm và tỉnh/thành cùng đang hoạt động")
public class LocationLookupRequest extends ProvinceLookupRequest {

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Lọc ID tỉnh/thành; bỏ trống lấy tất cả", minimum = "1")
    private Long provinceId;
}
