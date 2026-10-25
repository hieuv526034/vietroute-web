package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.common.base.BaseResponse;

/** Projection staff tường minh sau kiểm quyền/scope; không chứa mật khẩu/email/JWT. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Schema(description = "Dữ liệu Vehicle trong phạm vi được phép")
public class VehicleResponse extends BaseResponse {

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "ID phạm vi dữ liệu")
    private long scopeId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Mã nghiệp vụ")
    private String code;

    @Schema(description = "Biển số")
    private String plateNumber;

    @Schema(description = "ID loại xe")
    private long vehicleTypeId;

    @Schema(description = "Tên loại xe")
    private String vehicleTypeName;

    @Schema(description = "Số chỗ có thể bán")
    private int capacity;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "ID ảnh công khai")
    private Long photoMediaId;

    @Schema(
        description = "Có quyền scoped UPDATE tại thời điểm đọc; UI còn phải giao capabilities/policy chung, backend luôn kiểm lại"
    )
    private boolean canUpdate;

    public static VehicleResponse from(Vehicle item) {
        return VehicleResponse.builder()
            .id(item.getId())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .createdBy(item.getCreatedBy())
            .updatedBy(item.getUpdatedBy())
            .version(item.getVersion())
            .carrierId(item.getCarrierId())
            .scopeId(item.getScopeId())
            .canUpdate(item.isCanUpdate())
            .carrierName(item.getCarrierName())
            .code(item.getCode())
            .plateNumber(item.getPlateNumber())
            .vehicleTypeId(item.getVehicleTypeId())
            .vehicleTypeName(item.getVehicleTypeName())
            .capacity(item.getCapacity())
            .active(item.isActive())
            .photoMediaId(item.getPhotoMediaId())
            .build();
    }
}
