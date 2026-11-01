package vn.vietroute.business.journey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kết quả một cặp điểm, không có người liên hệ booking/driver account hoặc audit. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JourneyMatchResponse {

    @Schema(description = "ID chuyến")
    private long tripId;

    @Schema(description = "Mã chuyến")
    private String code;

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Tên loại xe Việt snapshot")
    private String vehicleTypeNameVi;

    @Schema(description = "Tên loại xe Anh snapshot")
    private String vehicleTypeNameEn;

    @Schema(description = "ID điểm đón snapshot")
    private long pickupStopId;

    @Schema(description = "ID điểm trả snapshot")
    private long dropoffStopId;

    @Schema(description = "Điểm đón Việt")
    private String pickupNameVi;

    @Schema(description = "Điểm đón Anh")
    private String pickupNameEn;

    @Schema(description = "Điểm trả Việt")
    private String dropoffNameVi;

    @Schema(description = "Điểm trả Anh")
    private String dropoffNameEn;

    @Schema(description = "Giờ đón UTC")
    private Instant pickupAt;

    @Schema(description = "Giờ trả UTC")
    private Instant dropoffAt;

    @Schema(description = "Giá một chỗ VND, chụp từ trip")
    private BigDecimal unitPrice;

    @Schema(description = "Chỗ còn nhỏ nhất trên mọi đoạn, có thể đổi trước xác nhận")
    private int availableSeats;

    @Schema(description = "Trạng thái chuyến")
    private String status;
}
