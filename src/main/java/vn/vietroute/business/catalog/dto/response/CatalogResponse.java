package vn.vietroute.business.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.common.base.BaseResponse;
import vn.vietroute.common.constant.Constants.CatalogType;

/** Mapping bằng builder kế thừa, lựa chọn tên theo locale tại lớp response. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Danh mục kèm tên đã chọn theo ngôn ngữ và audit")
public class CatalogResponse extends BaseResponse {

    @Schema(description = "Nhóm danh mục")
    private CatalogType type;

    @Schema(description = "Mã nghiệp vụ")
    private String code;

    @Schema(description = "Tên theo locale yêu cầu")
    private String name;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Thứ tự hiển thị")
    private int sortOrder;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    public static CatalogResponse from(CatalogItem item, Locale locale) {
        return CatalogResponse.builder()
            .id(item.getId())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .createdBy(item.getCreatedBy())
            .updatedBy(item.getUpdatedBy())
            .version(item.getVersion())
            .type(item.getType())
            .code(item.getCode())
            .name("en".equals(locale.getLanguage()) ? item.getNameEn() : item.getNameVi())
            .nameVi(item.getNameVi())
            .nameEn(item.getNameEn())
            .sortOrder(item.getSortOrder())
            .active(item.isActive())
            .build();
    }
}
