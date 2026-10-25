package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Version bắt buộc để optimistic update không ghi đè thay đổi của người khác. */
@Getter
@Setter
@Schema(description = "Cập nhật Carrier")
public class CarrierUpdateRequest extends CarrierWriteRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đã đọc mới nhất", minimum = "0")
    private Long version;
}
