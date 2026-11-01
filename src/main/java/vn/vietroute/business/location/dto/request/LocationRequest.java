package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Location; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class LocationRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt", maxLength = 120)
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Anh", maxLength = 120)
    private String nameEn;

    @Min(value = 0, message = "{validation.sortOrder}")
    @Max(value = 10000, message = "{validation.sortOrder}")
    @Schema(description = "Thứ tự hiển thị", minimum = "0", maximum = "10000", defaultValue = "0")
    private int sortOrder;

    @Schema(description = "Đang sử dụng; tắt giữ lại ID và lịch sử", defaultValue = "true")
    private boolean active = true;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.length}")
    @Schema(description = "Địa chỉ được công bố", maxLength = 255)
    private String address;

    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-90", message = "{validation.location.latitude}")
    @DecimalMax(value = "90", message = "{validation.location.latitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Vĩ độ WGS84, tối đa 6 chữ số thập phân", minimum = "-90", maximum = "90")
    private BigDecimal latitude;

    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-180", message = "{validation.location.longitude}")
    @DecimalMax(value = "180", message = "{validation.location.longitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Kinh độ WGS84, tối đa 6 chữ số thập phân", minimum = "-180", maximum = "180")
    private BigDecimal longitude;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã nội bộ bất biến, chữ hoa/số, 3–40 ký tự", example = "PLACE01")
    private String code;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID tỉnh/thành đang hoạt động; giữ nguyên sau khi tạo", minimum = "1")
    private Long provinceId;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class })
    @Schema(description = "Version đã đọc, bắt buộc để chống ghi đè", minimum = "0")
    private Long version;
}
