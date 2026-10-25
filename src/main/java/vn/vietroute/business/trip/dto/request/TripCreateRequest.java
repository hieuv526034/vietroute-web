package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.validation.BusinessCode;

/** Tạo bản nháp từ mẫu; status/audit/scope/giá/điểm là dữ liệu server. */
@Getter
@Setter
@Schema(description = "Tạo chuyến DRAFT từ tuyến đang sử dụng")
public class TripCreateRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @BusinessCode
    @Schema(description = "Mã chuyến bất biến, duy nhất trong nhà xe")
    private String code;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID mẫu tuyến cùng nhà xe/phạm vi")
    private long routeId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID xe active, cùng nhà xe")
    private long vehicleId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID hồ sơ tài xế active, liên kết DRIVER ready đúng scope")
    private long driverId;

    @NotNull(message = "{validation.required}")
    @Schema(description = "Mốc đầu tuyến UTC ISO-8601; sau thời điểm hiện tại, tối đa 2 năm")
    private Instant departureAt;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Chỗ bán 1–200, không vượt xe")
    private int capacity;
}
