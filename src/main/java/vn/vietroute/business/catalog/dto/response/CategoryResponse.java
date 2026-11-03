package vn.vietroute.business.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.common.base.BaseDTO;

/**
 * DTO quản trị chứa audit/version, tên song ngữ và props đã kiểm theo nhóm.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Danh mục quản trị; dữ liệu chung, props và audit")
public class CategoryResponse extends BaseDTO {

    @Schema(description = "Mã bất biến")
    private String code;

    @Schema(description = "GROUP hoặc VALUE")
    private String type;

    @Schema(description = "Mô tả danh mục")
    private String description;

    @Schema(description = "Code nhóm của VALUE")
    private String refType;

    @Schema(description = "ID giá trị cha")
    private Long parentId;

    @Schema(description = "Tên theo locale")
    private String name;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Thuộc tính JSON theo hợp đồng nhóm")
    private Map<String, Object> props;

    @Schema(description = "Đang sử dụng")
    private boolean active;

    @Schema(description = "Thứ tự hiển thị")
    private int sortOrder;

    public static CategoryResponse from(Category item, Locale locale) {
        CategoryResponse value = new CategoryResponse();
        value.setId(item.getId());
        value.setVersion(item.getVersion());
        value.setCreatedAt(item.getCreatedAt());
        value.setUpdatedAt(item.getUpdatedAt());
        value.setCreatedBy(item.getCreatedBy());
        value.setUpdatedBy(item.getUpdatedBy());
        value.setCode(item.getCode());
        value.setDescription(item.getDescription());
        value.setType(item.getType());
        value.setRefType(item.getRefType());
        value.setParentId(item.getParentId());
        value.setProps(new LinkedHashMap<>(item.getProps()));
        value.setNameVi(item.getName());
        value.setNameEn(String.valueOf(item.getProps().getOrDefault("nameEn", item.getName())));
        value.setName("en".equals(locale.getLanguage()) ? value.getNameEn() : value.getNameVi());
        value.setActive(item.isActive());
        value.setSortOrder(item.getSortOrder());
        return value;
    }
}
