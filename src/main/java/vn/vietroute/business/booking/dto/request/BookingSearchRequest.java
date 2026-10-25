package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.business.booking.entity.BookingStatus;
import vn.vietroute.common.base.PageRequest;

/** Owner hoặc scope áp dụng trong SQL trước count/page; không nhận customerId để mở dữ liệu khác. */
@Getter
@Setter
@Schema(description = "Tra đơn theo quyền và phân trang server")
public class BookingSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Mã đơn/mã chuyến/tên liên hệ, nguyên văn")
    private String query;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Lọc ID chuyến trong dữ liệu được phép")
    private Long tripId;

    @Schema(description = "Trạng thái đơn")
    private BookingStatus status;
}
