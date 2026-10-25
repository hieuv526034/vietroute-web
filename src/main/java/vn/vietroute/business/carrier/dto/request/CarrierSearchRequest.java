package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Mọi bộ lọc chạy ở PostgreSQL sau điều kiện scope; carrierId không tự cấp quyền. */
@Getter
@Setter
@Schema(description = "Tìm nhà xe/xe/tài xế theo trang")
public class CarrierSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Tìm theo mã, tên, điện thoại hoặc biển số")
    private String query;

    @Schema(description = "Trạng thái, bỏ trống để xem tất cả")
    private Boolean active;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID nhà xe lọc trong quyền hiện có")
    private Long carrierId;
}
