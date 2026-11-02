package vn.vietroute.business.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.catalog.entity.Province;

@Schema(description = "Tỉnh/thành công khai, không trả trạng thái nội bộ hoặc audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProvinceLookupResponse {

    @Schema(description = "ID tỉnh/thành")
    private long id;

    @Schema(description = "Mã nội bộ")
    private String code;

    @Schema(description = "Tên theo locale")
    private String name;

    public static ProvinceLookupResponse from(Province item, Locale locale) {
        return new ProvinceLookupResponse(
            item.getId(),
            item.getCode(),
            "en".equals(locale.getLanguage()) ? item.getNameEn() : item.getNameVi()
        );
    }
}
