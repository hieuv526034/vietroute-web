package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Sửa tỉnh/thành; ngừng sử dụng ẩn địa điểm con khỏi tra cứu công khai")
public class ProvinceUpdateRequest extends LocationNameRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc, bắt buộc để chống ghi đè", minimum = "0")
    private Long version;
}
