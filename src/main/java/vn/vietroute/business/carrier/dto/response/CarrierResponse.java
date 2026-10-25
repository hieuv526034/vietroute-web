package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.common.base.BaseResponse;

/** Projection staff tường minh sau kiểm quyền/scope; không chứa mật khẩu/email/JWT. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Schema(description = "Dữ liệu Carrier trong phạm vi được phép")
public class CarrierResponse extends BaseResponse {

    @Schema(description = "ID phạm vi dữ liệu")
    private long scopeId;

    @Schema(description = "Mã nghiệp vụ")
    private String code;

    @Schema(description = "Tên hiển thị")
    private String name;

    @Schema(description = "Điện thoại")
    private String phone;

    @Schema(description = "Mô tả")
    private String description;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "ID ảnh logo công khai")
    private Long logoMediaId;

    @Schema(
        description = "Có quyền scoped UPDATE tại thời điểm đọc; UI còn phải giao capabilities/policy chung, backend luôn kiểm lại"
    )
    private boolean canUpdate;

    public static CarrierResponse from(Carrier item) {
        return CarrierResponse.builder()
            .id(item.getId())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .createdBy(item.getCreatedBy())
            .updatedBy(item.getUpdatedBy())
            .version(item.getVersion())
            .scopeId(item.getScopeId())
            .canUpdate(item.isCanUpdate())
            .code(item.getCode())
            .name(item.getName())
            .phone(item.getPhone())
            .description(item.getDescription())
            .active(item.isActive())
            .logoMediaId(item.getLogoMediaId())
            .build();
    }
}
