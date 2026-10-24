package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Địa chỉ và tọa độ địa điểm; không gọi nhà cung cấp bản đồ")
public class LocationWriteRequest extends LocationNameRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.length}")
    @Schema(description = "Địa chỉ được công bố", maxLength = 255)
    private String address;

    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-90", message = "{validation.location.latitude}")
    @DecimalMax(value = "90", message = "{validation.location.latitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Vĩ độ WGS84, tối đa 6 chữ số thập phân", minimum = "-90", maximum = "90")
    private BigDecimal latitude;

    @NotNull(message = "{validation.required}")
    @DecimalMin(value = "-180", message = "{validation.location.longitude}")
    @DecimalMax(value = "180", message = "{validation.location.longitude}")
    @Digits(integer = 3, fraction = 6, message = "{validation.location.coordinatePrecision}")
    @Schema(description = "Kinh độ WGS84, tối đa 6 chữ số thập phân", minimum = "-180", maximum = "180")
    private BigDecimal longitude;
}
