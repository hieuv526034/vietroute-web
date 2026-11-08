package vn.vietroute.business.trip.dto.response;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.trip.constant.TripStatus;

/** Projection staff sau kiểm scope; API công khai sẽ dùng projection riêng. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TripResponse {

    @Schema(description = "ID chuyến")
    private long id;

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "Scope DB")
    private long scopeId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Điện thoại nhà xe")
    private String carrierPhone;

    @Schema(description = "ID mẫu tuyến")
    private long routeId;

    @Schema(description = "Mã chuyến")
    private String code;

    @Schema(description = "Tên Việt snapshot")
    private String nameVi;

    @Schema(description = "Tên Anh snapshot")
    private String nameEn;

    @Schema(description = "ID xe")
    private long vehicleId;

    @Schema(description = "Biển số snapshot")
    private String plateNumber;

    @Schema(description = "ID loại xe")
    private long vehicleTypeId;

    @Schema(description = "Tên loại xe Việt snapshot")
    private String vehicleTypeNameVi;

    @Schema(description = "Tên loại xe Anh snapshot")
    private String vehicleTypeNameEn;

    @Schema(description = "ID hồ sơ lái xe")
    private long driverId;

    @Schema(description = "ID account lái xe đã phân công")
    private long driverUserId;

    @Schema(description = "Tên tài xế snapshot")
    private String driverName;

    @Schema(description = "Điện thoại tài xế snapshot")
    private String driverPhone;

    @Schema(description = "Mốc đầu UTC")
    private Instant departureAt;

    @Schema(description = "Mốc cuối UTC")
    private Instant endAt;

    @Schema(description = "Số chỗ bán")
    private int capacity;

    @Schema(description = "Trạng thái chuyến")
    private TripStatus status;

    @Schema(description = "Chuyến nguồn đang sinh lịch chạy hằng ngày")
    private boolean dailyService;

    @Schema(description = "ID chuyến nguồn nếu sinh theo lịch cố định")
    private Long serviceSourceId;

    @Schema(description = "Thông báo vận hành")
    private String operationNote;

    @Schema(description = "Version")
    private long version;

    @Schema(description = "Có quyền UPDATE scoped, backend luôn kiểm lại")
    private boolean canUpdate;

    @Schema(description = "Điểm snapshot khi đọc chi tiết")
    private List<TripStopResponse> stops;

    @Schema(description = "Sự kiện vận hành mới nhất, tối đa 100")
    private List<TripEventResponse> events;

    /** Ánh xạ projection native; chỉ gọi sau kiểm quyền và phạm vi tại Repo. */
    public static TripResponse from(
        Map<String, Object> row,
        List<TripStopResponse> stops,
        List<TripEventResponse> events
    ) {
        return new TripResponse(
            longValue(row.get("id")),
            longValue(row.get("carrier_id")),
            longValue(row.get("scope_id")),
            stringValue(row.get("carrier_name")),
            stringValue(row.get("carrier_phone")),
            longValue(row.get("route_id")),
            stringValue(row.get("code")),
            stringValue(row.get("name_vi")),
            stringValue(row.get("name_en")),
            longValue(row.get("vehicle_id")),
            stringValue(row.get("plate_number")),
            longValue(row.get("vehicle_type_id")),
            stringValue(row.get("vehicle_type_name_vi")),
            stringValue(row.get("vehicle_type_name_en")),
            longValue(row.get("driver_id")),
            longValue(row.get("driver_user_id")),
            stringValue(row.get("driver_name")),
            stringValue(row.get("driver_phone")),
            instantValue(row.get("departure_at")),
            instantValue(row.get("end_at")),
            intValue(row.get("capacity")),
            TripStatus.valueOf(stringValue(row.get("status"))),
            booleanValue(row.get("daily_service")),
            row.get("service_source_id") == null ? null : longValue(row.get("service_source_id")),
            stringValue(row.get("operation_note")),
            longValue(row.get("version")),
            booleanValue(row.get("can_update")),
            stops,
            events
        );
    }
}
