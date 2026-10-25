package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Đổi phân công DRAFT/OPEN; giữ snapshot điểm/giờ/giá và không giảm dưới chỗ đã xác nhận. */
public record TripAssignmentRequest(
    @Min(value = 1, message = "{validation.invalid}") @Schema(description = "Xe active cùng nhà xe") long vehicleId,
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Hồ sơ DRIVER ready cùng nhà xe")
    long driverId,
    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Chỗ bán mới, không vượt xe hoặc thấp hơn chỗ đã xác nhận")
    int capacity,
    @Schema(description = "Giờ khởi hành UTC mới chỉ khi DRAFT; null giữ giờ cũ") Instant departureAt,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc")
    Long version,
    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Lý do đổi phân công")
    String note
) {}
