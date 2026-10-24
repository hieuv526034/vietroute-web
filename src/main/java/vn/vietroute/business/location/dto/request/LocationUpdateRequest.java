package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Sửa địa điểm, giữ nguyên mã và tỉnh/thành; không xóa lịch sử/tham chiếu")
public class LocationUpdateRequest extends LocationWriteRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc, bắt buộc để chống ghi đè", minimum = "0")
    private Long version;
}
