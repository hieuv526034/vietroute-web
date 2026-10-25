package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Endpoint typed cho bật lại bản ngừng dùng mà vẫn giữ code/ID/tham chiếu và version. */
@Getter
@Setter
@Schema(description = "Sửa và đổi trạng thái danh mục theo loại")
public class CatalogManageUpdateRequest extends CatalogUpdateRequest {

    @Schema(description = "Đang hoạt động", defaultValue = "true")
    private boolean active = true;
}
