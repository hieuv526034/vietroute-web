package vn.vietroute.business.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.catalog.entity.Category;

/**
 * Projection công khai dùng whitelist; không audit/version hoặc props GROUP/cấu hình nội bộ.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Lựa chọn danh mục công khai, không trả audit hoặc thuộc tính ngoài whitelist")
public class CategoryLookupResponse {

    @Schema(description = "ID giá trị danh mục")
    private Long id;

    @Schema(description = "Mã nghiệp vụ")
    private String code;

    @Schema(description = "Mô tả danh mục")
    private String description;

    @Schema(description = "Code nhóm")
    private String refType;

    @Schema(description = "Tên theo locale")
    private String name;

    @Schema(description = "ID cha")
    private Long parentId;

    @Schema(description = "Thuộc tính công khai được phép trả")
    private Map<String, Object> props;

    public static CategoryLookupResponse from(Category item, Locale locale) {
        CategoryLookupResponse value = new CategoryLookupResponse();
        value.setId(item.getId());
        value.setCode(item.getCode());
        value.setDescription(item.getDescription());
        value.setRefType(item.getRefType());
        value.setParentId(item.getParentId());
        boolean english = "en".equals(locale.getLanguage());
        value.setName(
            english ? String.valueOf(item.getProps().getOrDefault("nameEn", item.getName())) : item.getName()
        );
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : new String[] {
            "nameEn",
            "address",
            "latitude",
            "longitude",
            "seatCount",
            "administrativeCode",
            "divisionType",
        }) {
            if (item.getProps().containsKey(key)) safe.put(key, item.getProps().get(key));
        }
        value.setProps(safe);
        return value;
    }
}
