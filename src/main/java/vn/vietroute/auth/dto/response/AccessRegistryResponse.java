package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Registry role/quyền cấu hình, tách version khỏi version mapping")
public record AccessRegistryResponse(
    @Schema(description = "Mã ổn định") String code,
    @Schema(description = "Tên tiếng Việt") String nameVi,
    @Schema(description = "Tên tiếng Anh") String nameEn,
    @Schema(description = "Đang hoạt động") boolean active,
    @Schema(description = "Mã lõi không được vô hiệu hóa") boolean builtIn,
    @Schema(description = "Version registry") long version,
    @Schema(description = "Lần cập nhật gần nhất UTC") Instant updatedAt,
    @Schema(description = "ID người cập nhật, system là 0") long updatedBy
) {}
