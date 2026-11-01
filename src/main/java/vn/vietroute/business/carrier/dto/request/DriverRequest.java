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
@Schema(description = "Dữ liệu Driver; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class DriverRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Họ tên tài xế")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại liên hệ")
    private String phone;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Tài khoản DRIVER có membership đúng nhà xe; null khi chưa gắn account")
    private Long userId;

    @Schema(description = "Hồ sơ đang hoạt động")
    private boolean active = true;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã nghiệp vụ chữ hoa/số 3–40 ký tự, bắt đầu bằng chữ")
    private String code;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID nhà xe; server kiểm membership và scope")
    private Long carrierId;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class })
    @Schema(description = "Version đã đọc mới nhất", minimum = "0")
    private Long version;
}
