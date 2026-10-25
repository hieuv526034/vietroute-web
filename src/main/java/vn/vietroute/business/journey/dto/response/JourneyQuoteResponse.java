package vn.vietroute.business.journey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Chỉ là kết quả đọc; create/confirm luôn kiểm lại trong transaction, client không được gửi giá này. */
public record JourneyQuoteResponse(
    @Schema(description = "Giá một chỗ VND") BigDecimal unitPrice,
    @Schema(description = "Tổng giá cho số chỗ yêu cầu") BigDecimal totalPrice,
    @Schema(description = "Chỗ còn nhỏ nhất trên các đoạn") int availableSeats,
    @Schema(description = "Hiện đủ chỗ và đang nhận khách tại điểm đón") boolean accepting
) {}
