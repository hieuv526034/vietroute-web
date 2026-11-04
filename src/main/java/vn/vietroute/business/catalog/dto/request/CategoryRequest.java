package vn.vietroute.business.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.catalog.constant.CategoryRefType;
import vn.vietroute.common.validation.RequestGroups;

/**
 * Một hợp đồng tạo/sửa; thuộc tính riêng được kiểm theo metadata của GROUP.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Danh mục chung; identity code/type/refType/parentId giữ nguyên sau tạo")
public class CategoryRequest {

    @Size(max = 64, message = "{validation.length}")
    @Schema(description = "Mã ổn định; GROUP bắt buộc có mã", example = CategoryRefType.VEHICLE_TYPE)
    private String code;

    @Schema(description = "GROUP hoặc VALUE, mặc định VALUE", example = "VALUE")
    private String type = "VALUE";

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt", example = "Limousine")
    private String name;

    @NotNull(message = "{validation.required}")
    @Size(max = 1000, message = "{validation.length}")
    @Schema(description = "Mô tả hoặc hướng dẫn sử dụng danh mục", example = "Loại xe dùng khi khai báo phương tiện")
    private String description = "";

    @Size(max = 64, message = "{validation.length}")
    @NotBlank(message = "{validation.required}", groups = RequestGroups.Create.class)
    @Schema(description = "Nhóm danh mục bắt buộc; GROUP có refType bằng code", example = CategoryRefType.VEHICLE_TYPE)
    private String refType;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID giá trị cha đúng parentRefType của GROUP")
    private Long parentId;

    @NotNull(message = "{validation.required}")
    @Schema(description = "JSON object theo hợp đồng fields của nhóm; nameEn giữ bản dịch tiếng Anh")
    private Map<String, Object> props = new LinkedHashMap<>();

    @Schema(description = "Đang sử dụng", defaultValue = "true")
    private boolean active = true;

    @Min(value = 0, message = "{validation.sortOrder}")
    @Max(value = 10000, message = "{validation.sortOrder}")
    @Schema(description = "Thứ tự hiển thị", minimum = "0", maximum = "10000")
    private int sortOrder;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Update.class, RequestGroups.Delete.class })
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc; bắt buộc khi sửa/xóa", minimum = "0")
    private Long version;
}
