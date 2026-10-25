package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record BookingTripChoiceResponse(
    @Schema(description = "ID chuyến") long id,
    @Schema(description = "Mã chuyến") String code,
    @Schema(description = "Nhà xe") String carrierName,
    @Schema(description = "Tên Việt") String nameVi,
    @Schema(description = "Tên Anh") String nameEn,
    @Schema(description = "Giờ đầu tuyến UTC") Instant departureAt
) {}
