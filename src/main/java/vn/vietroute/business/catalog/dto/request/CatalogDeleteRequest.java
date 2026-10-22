package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

/** Xóa mềm giữ liên kết/audit; version bắt buộc để không xóa bản vừa được người khác sửa. */
@Getter
@Setter
@Schema(description = "Xóa mềm danh mục, giữ dữ liệu và audit để tra cứu quản trị")
public class CatalogDeleteRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành", minimum = "0", example = "1")
    private Long version;
}
