package vn.vietroute.business.location.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.location.entity.Province;
import vn.vietroute.common.base.BaseResponse;

/** Projection quản trị tường minh, chọn tên theo locale và giữ audit/version. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Tỉnh/thành quản trị")
public class ProvinceResponse extends BaseResponse {

    @Schema(description = "Tên theo locale")
    private String name;

    @Schema(description = "Mã nội bộ bất biến")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Thứ tự hiển thị")
    private int sortOrder;

    @Schema(description = "Đang sử dụng")
    private boolean active;

    public static ProvinceResponse from(Province item, Locale locale) {
        return ProvinceResponse.builder()
            .id(item.getId())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .createdBy(item.getCreatedBy())
            .updatedBy(item.getUpdatedBy())
            .version(item.getVersion())
            .code(item.getCode())
            .nameVi(item.getNameVi())
            .nameEn(item.getNameEn())
            .sortOrder(item.getSortOrder())
            .active(item.isActive())
            .name("en".equals(locale.getLanguage()) ? item.getNameEn() : item.getNameVi())
            .build();
    }
}
