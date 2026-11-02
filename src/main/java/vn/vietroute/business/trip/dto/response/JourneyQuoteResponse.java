package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ là kết quả đọc; create/confirm luôn kiểm lại trong transaction, client không được gửi giá này. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JourneyQuoteResponse {

    @Schema(description = "Giá một chỗ VND")
    private BigDecimal unitPrice;

    @Schema(description = "Tổng giá cho số chỗ yêu cầu")
    private BigDecimal totalPrice;

    @Schema(description = "Chỗ còn nhỏ nhất trên các đoạn")
    private int availableSeats;

    @Schema(description = "Hiện đủ chỗ và đang nhận khách tại điểm đón")
    private boolean accepting;
}
