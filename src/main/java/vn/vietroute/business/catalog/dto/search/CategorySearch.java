package vn.vietroute.business.catalog.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.catalog.constant.CategoryRefType;
import vn.vietroute.common.base.PageRequest;

/** Query chung; lookup công khai giới hạn nhóm và chỉ đọc mục đang sử dụng. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Lọc Category; query/nhóm/cha/trạng thái và phân trang tại DB")
public class CategorySearch extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "{validation.invalid}")
    @Schema(description = "Mã/tên hoặc địa chỉ, so khớp nguyên văn")
    private String query = "";

    @Size(max = 64, message = "{validation.length}")
    @Schema(description = "Code GROUP", example = CategoryRefType.LOCATION)
    private String refType;

    @Schema(description = "GROUP hoặc VALUE, quản trị mặc định VALUE")
    private String type = "VALUE";

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID giá trị cha")
    private Long parentId;

    @Schema(description = "Trạng thái; lookup công khai luôn ép đang sử dụng")
    private Boolean active;

    public Long parent() {
        return parentId;
    }
}
