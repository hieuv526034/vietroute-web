package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hồ sơ công bố không có scope, audit, phiên bản hoặc dữ liệu nhân sự/khách hàng. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PublicCarrierResponse {

    @Schema(description = "ID nhà xe công khai")
    private long id;

    @Schema(description = "Mã nhà xe")
    private String code;

    @Schema(description = "Tên thương mại")
    private String name;

    @Schema(description = "Điện thoại liên hệ được nhà xe công bố")
    private String phone;

    @Schema(description = "Giới thiệu được công bố")
    private String description;
}
