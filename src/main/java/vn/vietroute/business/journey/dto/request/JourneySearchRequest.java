package vn.vietroute.business.journey.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import vn.vietroute.common.base.PageRequest;

/** Ngày là ngày tại điểm đón ở Việt Nam, kể cả chuyến xuất phát từ hôm trước. */
@Getter
@Setter
@Schema(description = "Tìm chặng chuyến công khai theo địa điểm hoặc tỉnh và ngày tại điểm đón")
public class JourneySearchRequest extends PageRequest {

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID địa điểm đón, ưu tiên khi cùng gửi tỉnh")
    private Long fromLocationId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID tỉnh đón nếu không chọn địa điểm cụ thể")
    private Long fromProvinceId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID địa điểm trả, ưu tiên khi cùng gửi tỉnh")
    private Long toLocationId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID tỉnh trả nếu không chọn địa điểm cụ thể")
    private Long toProvinceId;

    @NotNull(message = "{validation.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Ngày đón Việt Nam yyyy-MM-dd")
    private LocalDate date;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Số chỗ tối thiểu trên mọi đoạn, mặc định 1")
    private int seats = 1;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Lọc nhà xe")
    private Long carrierId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Lọc loại xe")
    private Long vehicleTypeId;

    @AssertTrue(message = "{validation.invalid}")
    @Schema(hidden = true)
    public boolean isRouteDefined() {
        return (fromLocationId != null || fromProvinceId != null) && (toLocationId != null || toProvinceId != null);
    }
}
