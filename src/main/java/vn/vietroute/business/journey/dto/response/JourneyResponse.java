package vn.vietroute.business.journey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import vn.vietroute.business.trip.dto.response.TripStopResponse;

/** Projection công khai tường minh, không tái dùng staff TripResponse hoặc chứa PII hành khách/tài xế. */
public record JourneyResponse(
    @Schema(description = "ID chuyến") long id,
    @Schema(description = "Mã chuyến") String code,
    @Schema(description = "ID nhà xe") long carrierId,
    @Schema(description = "Tên nhà xe") String carrierName,
    @Schema(description = "Điện thoại liên hệ nhà xe") String carrierPhone,
    @Schema(description = "Tên tuyến Việt snapshot") String nameVi,
    @Schema(description = "Tên tuyến Anh snapshot") String nameEn,
    @Schema(description = "Loại xe Việt snapshot") String vehicleTypeNameVi,
    @Schema(description = "Loại xe Anh snapshot") String vehicleTypeNameEn,
    @Schema(description = "Biển số xe snapshot") String plateNumber,
    @Schema(description = "Mốc khởi hành đầu UTC") Instant departureAt,
    @Schema(description = "Mốc cuối UTC dự kiến") Instant endAt,
    @Schema(description = "Trạng thái chuyến") String status,
    @Schema(description = "Thông báo vận hành") String operationNote,
    @Schema(description = "Chụp điểm/giờ/giá và actual xác nhận") List<TripStopResponse> stops,
    @Schema(description = "Tồn chỗ từng đoạn tại lúc đọc") List<JourneySegmentResponse> segments
) {}
