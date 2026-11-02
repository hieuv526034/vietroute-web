package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Province; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class ProvinceRequest {

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

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã nội bộ bất biến, chữ hoa/số, 3–40 ký tự", example = "PROVINCE01")
    private String code;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class })
    @Schema(description = "Version đã đọc, bắt buộc để chống ghi đè", minimum = "0")
    private Long version;
}
