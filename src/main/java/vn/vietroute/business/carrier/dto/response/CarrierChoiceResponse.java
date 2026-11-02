package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.carrier.entity.Carrier;

/** Picker chỉ biết ID/mã/tên, không đòi thêm CARRIER_READ để tạo xe hoặc tài xế có quyền scoped. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CarrierChoiceResponse {

    @Schema(description = "ID nhà xe được phép tạo dữ liệu con")
    private long id;

    @Schema(description = "Mã nhà xe")
    private String code;

    @Schema(description = "Tên nhà xe")
    private String name;

    public static CarrierChoiceResponse from(Carrier carrier) {
        return new CarrierChoiceResponse(carrier.getId(), carrier.getCode(), carrier.getName());
    }
}
