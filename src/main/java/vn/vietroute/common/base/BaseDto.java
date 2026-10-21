package vn.vietroute.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** DTO audit dùng cho dữ liệu trả về; client không được tự gán các giá trị này. */
@Getter @Setter @SuperBuilder @NoArgsConstructor
@Schema(description = "Thông tin định danh và audit do server quản lý")
public abstract class BaseDto {
    @Schema(description = "ID bản ghi", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(description = "Thời điểm tạo theo UTC", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật theo UTC", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updatedAt;
    @Schema(description = "ID người tạo; null nếu bootstrap hệ thống", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createdBy;
    @Schema(description = "ID người cập nhật", accessMode = Schema.AccessMode.READ_ONLY)
    private Long updatedBy;
    @Schema(description = "Phiên bản bản ghi dùng kiểm soát cập nhật đồng thời", example = "0", accessMode = Schema.AccessMode.READ_ONLY)
    private long version;
}
