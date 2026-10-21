package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.validation.BusinessCode;

/** Ví dụ request nghiệp vụ: audit lấy từ principal; code không truyền thì service tự sinh. */
@Getter
@Setter
@Schema(description = "Tạo danh mục dùng chung; chỉ ADMIN có quyền")
public class CatalogCreateRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Schema(description = "Nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
    private CatalogType type;

    @BusinessCode
    @Schema(
        description = "Mã chữ hoa/số 3–40 ký tự, null để tự sinh",
        example = "SEATVIP",
        pattern = vn.vietroute.common.constant.Constants.CODE_PATTERN,
        minLength = 3,
        maxLength = 40
    )
    private String code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt", example = "Ghế ngồi cao cấp", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(
        description = "Tên tiếng Anh",
        example = "Premium seated coach",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String nameEn;

    @Min(value = 0, message = "{validation.sortOrder}")
    @Max(value = 10000, message = "{validation.sortOrder}")
    @Schema(description = "Thứ tự hiển thị", example = "10", minimum = "0", maximum = "10000")
    private int sortOrder;
}
