package vn.vietroute.business.journey.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Báo giá/chỗ tại lúc đọc, không giữ chỗ")
public class JourneyQuoteRequest {

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm đón của trip")
    private long pickupStopId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm trả của trip")
    private long dropoffStopId;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Số chỗ cần, mặc định 1")
    private int seats = 1;
}
