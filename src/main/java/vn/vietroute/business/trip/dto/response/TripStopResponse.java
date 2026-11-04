package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.trip.dto.projection.TripStopProjection;

/** Snapshot độc lập danh mục/mẫu tuyến; không trả audit/actor riêng tư trên màn công khai. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TripStopResponse {

    @Schema(description = "ID điểm của chính chuyến")
    private long id;

    @Schema(description = "ID địa điểm nguồn")
    private long locationId;

    @Schema(description = "ID tỉnh nguồn")
    private long provinceId;

    @Schema(description = "Thứ tự")
    private int stopOrder;

    @Schema(description = "Tên Việt snapshot")
    private String nameVi;

    @Schema(description = "Tên Anh snapshot")
    private String nameEn;

    @Schema(description = "Địa chỉ snapshot")
    private String address;

    @Schema(description = "Vĩ độ")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ")
    private BigDecimal longitude;

    @Schema(description = "Đến dự kiến UTC")
    private Instant scheduledArrivalAt;

    @Schema(description = "Rời dự kiến UTC")
    private Instant scheduledDepartureAt;

    @Schema(description = "Tài xế xác nhận đến, null nếu chưa đến")
    private Instant actualArrivalAt;

    @Schema(description = "Tài xế xác nhận rời, null nếu chưa rời")
    private Instant actualDepartureAt;

    @Schema(description = "Giá cộng dồn snapshot")
    private BigDecimal cumulativeFare;

    @Schema(description = "Được đón khách")
    private boolean pickupAllowed;

    @Schema(description = "Được trả khách")
    private boolean dropoffAllowed;

    /** Ánh xạ projection native; chỉ gọi sau kiểm quyền và phạm vi tại Repo. */
    public static TripStopResponse from(TripStopProjection row) {
        return new TripStopResponse(
            row.getId(),
            row.getLocationId(),
            row.getProvinceId(),
            row.getStopOrder(),
            row.getNameVi(),
            row.getNameEn(),
            row.getAddress(),
            row.getLatitude(),
            row.getLongitude(),
            row.getScheduledArrivalAt(),
            row.getScheduledDepartureAt(),
            row.getActualArrivalAt(),
            row.getActualDepartureAt(),
            row.getCumulativeFare(),
            row.getPickupAllowed(),
            row.getDropoffAllowed()
        );
    }
}
