package vn.vietroute.business.route.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Thứ tự lấy từ vị trí phần tử, không nhận scope/audit hoặc tên địa điểm từ client. */
public record RouteStopRequest(
    @Min(value = 1, message = "{validation.invalid}") @Schema(description = "ID địa điểm có thật") long locationId,
    @Min(value = 0, message = "{validation.invalid}")
    @Max(value = 43200, message = "{validation.invalid}")
    @Schema(description = "Phút đến kể từ mốc xuất phát, cho phép qua nhiều ngày")
    int arrivalOffsetMinutes,
    @Min(value = 0, message = "{validation.invalid}")
    @Max(value = 43200, message = "{validation.invalid}")
    @Schema(description = "Phút rời, không nhỏ hơn phút đến")
    int departureOffsetMinutes,
    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "0", message = "{validation.invalid}")
    @Digits(integer = 10, fraction = 2, message = "{validation.invalid}")
    @Schema(description = "Giá cộng dồn VND, không giảm theo thứ tự")
    BigDecimal cumulativeFare,
    @Schema(description = "Cho phép đón khách tại điểm") boolean pickupAllowed,
    @Schema(description = "Cho phép trả khách tại điểm") boolean dropoffAllowed
) {}
