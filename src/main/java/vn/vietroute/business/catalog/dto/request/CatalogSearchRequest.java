package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Lọc tên/mã/trạng thái trong DB; màn từng loại không nhận type do client chọn. */
@Getter
@Setter
@Schema(description = "Tìm danh mục quản trị")
public class CatalogSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Tìm mã hoặc tên Việt/Anh")
    private String query;

    @Schema(description = "Trạng thái, bỏ trống để xem tất cả")
    private Boolean active;
}
