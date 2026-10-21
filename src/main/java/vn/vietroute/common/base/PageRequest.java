package vn.vietroute.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Phân trang bắt đầu từ 0; tên cột sort phải được service whitelist riêng. */
@Getter
@Setter
@Schema(description = "Tham số phân trang, page bắt đầu từ 0")
public class PageRequest extends BaseRequest {

    @Min(value = 0, message = "{validation.page}")
    @Max(value = 1000000, message = "{validation.page}")
    @Schema(description = "Chỉ số trang", defaultValue = "0", minimum = "0", maximum = "1000000")
    private int page;

    @Min(value = 1, message = "{validation.size}")
    @Max(value = 100, message = "{validation.size}")
    @Schema(description = "Số bản ghi mỗi trang", defaultValue = "20", minimum = "1", maximum = "100")
    private int size = 20;

    /** Dùng long để không tràn số khi tính OFFSET. */
    public long offset() {
        return (long) page * size;
    }
}
