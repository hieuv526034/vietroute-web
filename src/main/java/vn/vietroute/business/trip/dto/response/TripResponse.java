package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import vn.vietroute.business.trip.entity.TripStatus;

/** Projection staff sau kiểm scope; API công khai sẽ dùng projection riêng. */
public record TripResponse(
    @Schema(description = "ID chuyến") long id,
    @Schema(description = "ID nhà xe") long carrierId,
    @Schema(description = "Scope DB") long scopeId,
    @Schema(description = "Tên nhà xe") String carrierName,
    @Schema(description = "Điện thoại nhà xe") String carrierPhone,
    @Schema(description = "ID mẫu tuyến") long routeId,
    @Schema(description = "Mã chuyến") String code,
    @Schema(description = "Tên Việt snapshot") String nameVi,
    @Schema(description = "Tên Anh snapshot") String nameEn,
    @Schema(description = "ID xe") long vehicleId,
    @Schema(description = "Biển số snapshot") String plateNumber,
    @Schema(description = "ID loại xe") long vehicleTypeId,
    @Schema(description = "Tên loại xe Việt snapshot") String vehicleTypeNameVi,
    @Schema(description = "Tên loại xe Anh snapshot") String vehicleTypeNameEn,
    @Schema(description = "ID hồ sơ lái xe") long driverId,
    @Schema(description = "ID account lái xe đã phân công") long driverUserId,
    @Schema(description = "Tên tài xế snapshot") String driverName,
    @Schema(description = "Điện thoại tài xế snapshot") String driverPhone,
    @Schema(description = "Mốc đầu UTC") Instant departureAt,
    @Schema(description = "Mốc cuối UTC") Instant endAt,
    @Schema(description = "Số chỗ bán") int capacity,
    @Schema(description = "Trạng thái chuyến") TripStatus status,
    @Schema(description = "Thông báo vận hành") String operationNote,
    @Schema(description = "Version") long version,
    @Schema(description = "Có quyền UPDATE scoped, backend luôn kiểm lại") boolean canUpdate,
    @Schema(description = "Điểm snapshot khi đọc chi tiết") List<TripStopResponse> stops,
    @Schema(description = "Sự kiện vận hành mới nhất, tối đa 100") List<TripEventResponse> events
) {}
