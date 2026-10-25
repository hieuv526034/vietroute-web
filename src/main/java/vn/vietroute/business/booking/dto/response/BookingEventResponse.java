package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Lịch sử đơn không trả actor username/email hoặc request key. */
public record BookingEventResponse(
    @Schema(description = "ID sự kiện") long id,
    @Schema(description = "Loại sự kiện") String eventType,
    @Schema(description = "Trạng thái trước, null lúc tạo") String fromStatus,
    @Schema(description = "Trạng thái sau") String toStatus,
    @Schema(description = "Số tiền ghi nhận ở PAYMENT/REFUND, 0 với trạng thái") BigDecimal amount,
    @Schema(description = "Lý do/ghi chú") String note,
    @Schema(description = "Thời gian server UTC") Instant createdAt
) {}
