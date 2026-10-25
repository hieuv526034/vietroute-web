package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.vietroute.business.trip.entity.TripStatus;

/** Staff chỉ mở/hủy; bắt đầu/kết thúc do API tài xế đúng người phụ trách. */
public record TripStateRequest(
    @NotNull(message = "{validation.required}")
    @Schema(description = "OPEN hoặc CANCELLED; các trạng thái khác không nhận từ staff API")
    TripStatus status,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc")
    Long version,
    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Lý do/thông báo, có thể rỗng khi mở")
    String note
) {}
