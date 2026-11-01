package vn.vietroute.business.location.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.location.entity.Location;

@Schema(description = "Địa điểm công khai; không trả audit hoặc dữ liệu đã ngừng sử dụng")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LocationLookupResponse {

    @Schema(description = "ID địa điểm")
    private long id;

    @Schema(description = "Mã nội bộ")
    private String code;

    @Schema(description = "Tên theo locale")
    private String name;

    @Schema(description = "ID tỉnh/thành")
    private long provinceId;

    @Schema(description = "Tên tỉnh/thành theo locale")
    private String provinceName;

    @Schema(description = "Địa chỉ công bố")
    private String address;

    @Schema(description = "Vĩ độ WGS84")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ WGS84")
    private BigDecimal longitude;

    public static LocationLookupResponse from(Location item, Locale locale) {
        boolean en = "en".equals(locale.getLanguage());
        return new LocationLookupResponse(
            item.getId(),
            item.getCode(),
            en ? item.getNameEn() : item.getNameVi(),
            item.getProvinceId(),
            en ? item.getProvinceNameEn() : item.getProvinceNameVi(),
            item.getAddress(),
            item.getLatitude(),
            item.getLongitude()
        );
    }
}
