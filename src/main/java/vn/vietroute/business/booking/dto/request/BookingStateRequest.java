package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.vietroute.business.booking.entity.BookingStatus;

/** Staff CONFIRMED/REJECTED/CANCELLED; khách chỉ CANCELLED, cùng kiểm version và đồ thị trạng thái. */
public record BookingStateRequest(
    @NotNull(message = "{validation.required}")
    @Schema(description = "Trạng thái đích, server kiểm theo endpoint/vai trò")
    BookingStatus status,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đơn đã đọc")
    Long version,
    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Lý do; bắt buộc khi hủy/từ chối")
    String note
) {}
