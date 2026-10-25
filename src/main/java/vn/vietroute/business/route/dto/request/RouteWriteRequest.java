package vn.vietroute.business.route.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.validation.BusinessCode;

/** Tuyến và toàn bộ thứ tự điểm được lưu nguyên tử; code/carrier bất biến sau tạo. */
@Getter
@Setter
@Schema(description = "Mẫu tuyến một chiều; hai chiều là hai bản ghi riêng")
public class RouteWriteRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Nhà xe trong quyền hiện có; không tự cấp phạm vi")
    private Long carrierId;

    @NotBlank(message = "{validation.required}")
    @BusinessCode
    @Schema(description = "Mã tuyến chữ hoa/số 3–40 ký tự")
    private String code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @NotNull(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Mô tả, có thể rỗng")
    private String description = "";

    @Schema(description = "Tuyến được sử dụng để tạo chuyến mới")
    private boolean active = true;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Bỏ trống khi tạo; bắt buộc version đã đọc khi cập nhật")
    private Long version;

    @NotNull(message = "{validation.required}")
    @Size(min = 2, max = 32, message = "{validation.length}")
    @Valid
    @Schema(description = "2–32 điểm theo đúng thứ tự, không lặp địa điểm")
    private List<@NotNull(message = "{validation.required}") @Valid RouteStopRequest> stops;
}
