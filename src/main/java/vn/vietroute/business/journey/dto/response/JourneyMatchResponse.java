package vn.vietroute.business.journey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Kết quả một cặp điểm, không có người liên hệ booking/driver account hoặc audit. */
public record JourneyMatchResponse(
    @Schema(description = "ID chuyến") long tripId,
    @Schema(description = "Mã chuyến") String code,
    @Schema(description = "ID nhà xe") long carrierId,
    @Schema(description = "Tên nhà xe") String carrierName,
    @Schema(description = "Tên loại xe Việt snapshot") String vehicleTypeNameVi,
    @Schema(description = "Tên loại xe Anh snapshot") String vehicleTypeNameEn,
    @Schema(description = "ID điểm đón snapshot") long pickupStopId,
    @Schema(description = "ID điểm trả snapshot") long dropoffStopId,
    @Schema(description = "Điểm đón Việt") String pickupNameVi,
    @Schema(description = "Điểm đón Anh") String pickupNameEn,
    @Schema(description = "Điểm trả Việt") String dropoffNameVi,
    @Schema(description = "Điểm trả Anh") String dropoffNameEn,
    @Schema(description = "Giờ đón UTC") Instant pickupAt,
    @Schema(description = "Giờ trả UTC") Instant dropoffAt,
    @Schema(description = "Giá một chỗ VND, chụp từ trip") BigDecimal unitPrice,
    @Schema(description = "Chỗ còn nhỏ nhất trên mọi đoạn, có thể đổi trước xác nhận") int availableSeats,
    @Schema(description = "Trạng thái chuyến") String status
) {}
