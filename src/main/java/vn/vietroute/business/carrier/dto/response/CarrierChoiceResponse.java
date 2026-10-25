package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.business.carrier.entity.Carrier;

/** Picker chỉ biết ID/mã/tên, không đòi thêm CARRIER_READ để tạo xe hoặc tài xế có quyền scoped. */
public record CarrierChoiceResponse(
    @Schema(description = "ID nhà xe được phép tạo dữ liệu con") long id,
    @Schema(description = "Mã nhà xe") String code,
    @Schema(description = "Tên nhà xe") String name
) {
    public static CarrierChoiceResponse from(Carrier carrier) {
        return new CarrierChoiceResponse(carrier.getId(), carrier.getCode(), carrier.getName());
    }
}
