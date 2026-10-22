package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

/** Chỉ sửa tên/thứ tự; type/code là định danh nghiệp vụ không được đổi bằng API này. */
@Getter
@Setter
@Schema(description = "Cập nhật danh mục với version chống ghi đè thay đổi đồng thời")
public class CatalogUpdateRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt mới", example = "Ghế ngồi cao cấp")
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Anh mới", example = "Premium seated coach")
    private String nameEn;

    @Min(value = 0, message = "{validation.sortOrder}")
    @Max(value = 10000, message = "{validation.sortOrder}")
    @Schema(description = "Thứ tự hiển thị", minimum = "0", maximum = "10000")
    private int sortOrder;

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của danh mục", minimum = "0", example = "0")
    private Long version;
}
