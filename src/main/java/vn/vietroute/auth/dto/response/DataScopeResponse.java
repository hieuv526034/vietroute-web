package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description = "Phạm vi bảo mật; quan hệ nhà xe nghiệp vụ sẽ tham chiếu ID này, không dùng cookie/client role làm scope"
)
public record DataScopeResponse(
    @Schema(description = "ID scope do server cấp") long id,
    @Schema(description = "Mã bất biến") String code,
    @Schema(description = "Tên tiếng Việt") String nameVi,
    @Schema(description = "Tên tiếng Anh") String nameEn,
    @Schema(description = "Phạm vi bảo mật đang bật") boolean active,
    @Schema(description = "Phiên bản metadata") long version,
    @Schema(description = "Thời điểm cập nhật UTC") Instant updatedAt,
    @Schema(description = "Actor cập nhật; 0 là system") long updatedBy
) {}
