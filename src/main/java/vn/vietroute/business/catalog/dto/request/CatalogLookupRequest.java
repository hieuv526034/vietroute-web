package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Public chỉ nhận tìm/phân trang, không có cờ active để nới dữ liệu công khai. */
@Getter
@Setter
@Schema(description = "Tra cứu danh mục active")
public class CatalogLookupRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Tìm mã hoặc tên Việt/Anh")
    private String query;
}
