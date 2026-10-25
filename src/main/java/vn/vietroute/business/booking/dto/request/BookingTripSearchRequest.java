package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

@Getter
@Setter
@Schema(description = "Chọn trip đang nhận khách trong BOOKING_CREATE scope")
public class BookingTripSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Mã chuyến/tên tuyến/nhà xe, nguyên văn")
    private String query;
}
