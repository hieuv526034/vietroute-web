package vn.vietroute.business.location.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.location.entity.Location;
import vn.vietroute.common.base.BaseResponse;

/** Projection quản trị tường minh, chọn tên theo locale và giữ audit/version. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Địa điểm quản trị")
public class LocationResponse extends BaseResponse {

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

    @Schema(description = "ID tỉnh/thành bất biến")
    private Long provinceId;

    @Schema(description = "Tên tỉnh/thành tiếng Việt")
    private String provinceNameVi;

    @Schema(description = "Tên tỉnh/thành tiếng Anh")
    private String provinceNameEn;

    @Schema(description = "Tỉnh/thành đang hoạt động; địa điểm chỉ công khai khi cả hai cùng bật")
    private boolean provinceActive;

    @Schema(description = "Địa chỉ công bố")
    private String address;

    @Schema(description = "Vĩ độ WGS84")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ WGS84")
    private BigDecimal longitude;

    public static LocationResponse from(Location item, Locale locale) {
        return LocationResponse.builder()
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
            .provinceId(item.getProvinceId())
            .provinceNameVi(item.getProvinceNameVi())
            .provinceNameEn(item.getProvinceNameEn())
            .provinceActive(item.isProvinceActive())
            .address(item.getAddress())
            .latitude(item.getLatitude())
            .longitude(item.getLongitude())
            .name("en".equals(locale.getLanguage()) ? item.getNameEn() : item.getNameVi())
            .build();
    }
}
