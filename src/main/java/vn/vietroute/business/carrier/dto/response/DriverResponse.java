package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.common.base.BaseResponse;

/** Projection staff tường minh sau kiểm quyền/scope; không chứa mật khẩu/email/JWT. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Schema(description = "Dữ liệu Driver trong phạm vi được phép")
public class DriverResponse extends BaseResponse {

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "ID phạm vi dữ liệu")
    private long scopeId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Mã nghiệp vụ")
    private String code;

    @Schema(description = "Tên hiển thị")
    private String name;

    @Schema(description = "Điện thoại")
    private String phone;

    @Schema(description = "ID tài khoản DRIVER đã liên kết")
    private Long userId;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(
        description = "Có quyền scoped UPDATE tại thời điểm đọc; UI còn phải giao capabilities/policy chung, backend luôn kiểm lại"
    )
    private boolean canUpdate;

    public static DriverResponse from(Driver item) {
        return DriverResponse.builder()
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
            .name(item.getName())
            .phone(item.getPhone())
            .userId(item.getUserId())
            .active(item.isActive())
            .build();
    }
}
