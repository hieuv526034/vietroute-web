package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Actor/thời gian actual lấy từ server; client chỉ chọn hành động và điểm kế tiếp. */
public record DriverProgressRequest(
    @NotNull(message = "{validation.required}")
    @Schema(description = "START/ARRIVE/DEPART/COMPLETE, backend kiểm thứ tự")
    Action action,
    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 32, message = "{validation.invalid}")
    @Schema(description = "Thứ tự điểm của chính chuyến; START=1, COMPLETE=điểm cuối")
    int stopOrder,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version chuyến vừa đọc")
    Long version,
    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Ghi chú vận hành, có thể rỗng")
    String note
) {
    public enum Action {
        START,
        ARRIVE,
        DEPART,
        COMPLETE,
    }
}
