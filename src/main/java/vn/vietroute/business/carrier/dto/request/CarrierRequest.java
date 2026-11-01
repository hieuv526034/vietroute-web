package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Carrier; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class CarrierRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên thương mại nhà xe")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại công bố")
    private String phone;

    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Mô tả nhà xe")
    private String description = "";

    @Schema(description = "Đang nhận nghiệp vụ mới")
    private boolean active = true;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID ảnh logo READY do chính actor sở hữu, null để gỡ")
    private Long logoMediaId;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã nghiệp vụ chữ hoa/số 3–40 ký tự, bắt đầu bằng chữ")
    private String code;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class })
    @Schema(description = "Version đã đọc mới nhất", minimum = "0")
    private Long version;
}
