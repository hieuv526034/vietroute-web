package vn.vietroute.business.location.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Locale;
import vn.vietroute.business.location.entity.Location;

@Schema(description = "Địa điểm công khai; không trả audit hoặc dữ liệu đã ngừng sử dụng")
public record LocationLookupResponse(
    @Schema(description = "ID địa điểm") long id,
    @Schema(description = "Mã nội bộ") String code,
    @Schema(description = "Tên theo locale") String name,
    @Schema(description = "ID tỉnh/thành") long provinceId,
    @Schema(description = "Tên tỉnh/thành theo locale") String provinceName,
    @Schema(description = "Địa chỉ công bố") String address,
    @Schema(description = "Vĩ độ WGS84") BigDecimal latitude,
    @Schema(description = "Kinh độ WGS84") BigDecimal longitude
) {
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
