package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

/** Các trường biên tập chung; code/ID/audit không được thay qua payload sửa. */
@Getter
@Setter
@Schema(description = "Tên vi/en, thứ tự và trạng thái danh mục địa điểm")
public class LocationNameRequest extends BaseRequest {

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
}
