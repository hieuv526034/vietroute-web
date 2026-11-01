package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BookingTripChoiceResponse {

    @Schema(description = "ID chuyến")
    private long id;

    @Schema(description = "Mã chuyến")
    private String code;

    @Schema(description = "Nhà xe")
    private String carrierName;

    @Schema(description = "Tên Việt")
    private String nameVi;

    @Schema(description = "Tên Anh")
    private String nameEn;

    @Schema(description = "Giờ đầu tuyến UTC")
    private Instant departureAt;
}
