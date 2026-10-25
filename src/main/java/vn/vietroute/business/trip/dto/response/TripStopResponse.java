package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot độc lập danh mục/mẫu tuyến; không trả audit/actor riêng tư trên màn công khai. */
public record TripStopResponse(
    @Schema(description = "ID điểm của chính chuyến") long id,
    @Schema(description = "ID địa điểm nguồn") long locationId,
    @Schema(description = "ID tỉnh nguồn") long provinceId,
    @Schema(description = "Thứ tự") int stopOrder,
    @Schema(description = "Tên Việt snapshot") String nameVi,
    @Schema(description = "Tên Anh snapshot") String nameEn,
    @Schema(description = "Địa chỉ snapshot") String address,
    @Schema(description = "Vĩ độ") BigDecimal latitude,
    @Schema(description = "Kinh độ") BigDecimal longitude,
    @Schema(description = "Đến dự kiến UTC") Instant scheduledArrivalAt,
    @Schema(description = "Rời dự kiến UTC") Instant scheduledDepartureAt,
    @Schema(description = "Tài xế xác nhận đến, null nếu chưa đến") Instant actualArrivalAt,
    @Schema(description = "Tài xế xác nhận rời, null nếu chưa rời") Instant actualDepartureAt,
    @Schema(description = "Giá cộng dồn snapshot") BigDecimal cumulativeFare,
    @Schema(description = "Được đón khách") boolean pickupAllowed,
    @Schema(description = "Được trả khách") boolean dropoffAllowed
) {}
