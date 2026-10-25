package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.common.base.PageRequest;

@Getter
@Setter
@Schema(description = "Tìm chuyến theo scope; ngày là ngày xuất phát tại Việt Nam")
public class TripSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Từ khóa mã chuyến/tên tuyến/biển số, nguyên văn")
    private String query;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID nhà xe trong scope hiện hành")
    private Long carrierId;

    @Schema(description = "Lọc trạng thái, bỏ trống xem tất cả trong quyền")
    private TripStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Ngày xuất phát ở múi giờ Việt Nam, yyyy-MM-dd")
    private LocalDate date;
}
