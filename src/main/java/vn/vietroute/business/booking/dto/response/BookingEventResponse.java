package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch sử đơn không trả actor username/email hoặc request key. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BookingEventResponse {

    @Schema(description = "ID sự kiện")
    private long id;

    @Schema(description = "Loại sự kiện")
    private String eventType;

    @Schema(description = "Trạng thái trước, null lúc tạo")
    private String fromStatus;

    @Schema(description = "Trạng thái sau")
    private String toStatus;

    @Schema(description = "Số tiền ghi nhận ở PAYMENT/REFUND, 0 với trạng thái")
    private BigDecimal amount;

    @Schema(description = "Lý do/ghi chú")
    private String note;

    @Schema(description = "Thời gian server UTC")
    private Instant createdAt;
}
