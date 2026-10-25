package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Chỉ ghi nhận thu/hoàn đã diễn ra ngoài hệ thống, không gọi cổng hoặc tự coi hủy là hoàn tiền. */
public record BookingPaymentRequest(
    @NotNull(message = "{validation.required}") @Schema(description = "PAYMENT hoặc REFUND") Kind kind,
    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "0.01", message = "{validation.invalid}")
    @Digits(integer = 13, fraction = 2, message = "{validation.invalid}")
    @Schema(description = "Số tiền đã thu/hoàn thực tế VND, tối đa còn phải thu/hoàn")
    BigDecimal amount,
    @NotBlank(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Ghi chú/chứng từ ghi nhận thủ công")
    String note,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đơn")
    Long version,
    @NotNull(message = "{validation.required}") @Schema(description = "UUID chống ghi nhận tiền lặp") UUID requestKey
) {
    public enum Kind {
        PAYMENT,
        REFUND,
    }
}
