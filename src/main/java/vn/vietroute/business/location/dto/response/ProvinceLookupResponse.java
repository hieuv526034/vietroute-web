package vn.vietroute.business.location.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import vn.vietroute.business.location.entity.Province;

@Schema(description = "Tỉnh/thành công khai, không trả trạng thái nội bộ hoặc audit")
public record ProvinceLookupResponse(
    @Schema(description = "ID tỉnh/thành") long id,
    @Schema(description = "Mã nội bộ") String code,
    @Schema(description = "Tên theo locale") String name
) {
    public static ProvinceLookupResponse from(Province item, Locale locale) {
        return new ProvinceLookupResponse(
            item.getId(),
            item.getCode(),
            "en".equals(locale.getLanguage()) ? item.getNameEn() : item.getNameVi()
        );
    }
}
