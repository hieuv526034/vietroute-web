package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.trip.dto.response.TripStopResponse;

/** Projection công khai tường minh, không tái dùng staff TripResponse hoặc chứa PII hành khách/tài xế. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JourneyResponse {

    @Schema(description = "ID chuyến")
    private long id;

    @Schema(description = "Mã chuyến")
    private String code;

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Điện thoại liên hệ nhà xe")
    private String carrierPhone;

    @Schema(description = "Tên tuyến Việt snapshot")
    private String nameVi;

    @Schema(description = "Tên tuyến Anh snapshot")
    private String nameEn;

    @Schema(description = "Loại xe Việt snapshot")
    private String vehicleTypeNameVi;

    @Schema(description = "Loại xe Anh snapshot")
    private String vehicleTypeNameEn;

    @Schema(description = "Biển số xe snapshot")
    private String plateNumber;

    @Schema(description = "Mốc khởi hành đầu UTC")
    private Instant departureAt;

    @Schema(description = "Mốc cuối UTC dự kiến")
    private Instant endAt;

    @Schema(description = "Trạng thái chuyến")
    private String status;

    @Schema(description = "Thông báo vận hành")
    private String operationNote;

    @Schema(description = "Chụp điểm/giờ/giá và actual xác nhận")
    private List<TripStopResponse> stops;

    @Schema(description = "Tồn chỗ từng đoạn tại lúc đọc")
    private List<JourneySegmentResponse> segments;
}
