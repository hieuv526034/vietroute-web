package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Picker theo quyền TRIP, không cần cấp thêm READ xe/tài xế/tuyến. */
@Getter
@Setter
@Schema(description = "Tìm một loại dữ liệu phục vụ tạo/phân công chuyến")
public class TripChoiceRequest extends PageRequest {

    public enum Kind {
        CARRIER,
        ROUTE,
        VEHICLE,
        DRIVER,
    }

    @NotNull(message = "{validation.required}")
    @Schema(description = "Loại picker CARRIER/ROUTE/VEHICLE/DRIVER")
    private Kind kind;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Nhà xe cần chọn dữ liệu con; bỏ trống chỉ khi kind=CARRIER")
    private Long carrierId;

    @NotNull(message = "{validation.required}")
    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Từ khóa mã/tên/biển số, nguyên văn")
    private String query = "";
}
