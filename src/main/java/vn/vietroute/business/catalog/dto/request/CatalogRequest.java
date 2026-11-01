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
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Catalog; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class CatalogRequest extends BaseRequest {

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Schema(description = "Nhóm danh mục")
    private CatalogType type;

    @BusinessCode
    @Schema(
        description = "Mã chữ hoa/số 3–40 ký tự, null để tự sinh",
        example = "SEATVIP",
        pattern = Regex.BUSINESS_CODE,
        minLength = 3,
        maxLength = 40
    )
    private String code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt", example = "Ghế ngồi cao cấp")
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Anh", example = "Premium seated coach")
    private String nameEn;

    @Min(value = 0, message = "{validation.sortOrder}")
    @Max(value = 10000, message = "{validation.sortOrder}")
    @Schema(description = "Thứ tự hiển thị", example = "10", minimum = "0", maximum = "10000")
    private int sortOrder;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class, RequestGroups.Delete.class })
    @Min(value = 0, message = "{validation.invalid}", groups = { Default.class, RequestGroups.Delete.class })
    @Schema(description = "Version hiện hành của danh mục", minimum = "0", example = "0")
    private Long version;

    @Schema(description = "Đang hoạt động", defaultValue = "true")
    private boolean active = true;
}
