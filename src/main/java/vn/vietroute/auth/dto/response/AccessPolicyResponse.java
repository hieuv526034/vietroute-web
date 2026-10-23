package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

@Schema(description = "Chính sách bổ sung, không thay thế các kiểm tra bắt buộc trong code")
public record AccessPolicyResponse(
    @Schema(description = "ID chính sách") long id,
    @Schema(description = "MENU hoặc ENDPOINT") String kind,
    @Schema(description = "Phương thức và template route") String target,
    @Schema(description = "Tên tiếng Việt") String nameVi,
    @Schema(description = "Tên tiếng Anh") String nameEn,
    @Schema(description = "Cho phép truy cập khi đáp ứng điều kiện") boolean enabled,
    @Schema(description = "Tất cả quyền bắt buộc") Set<String> permissions,
    @Schema(description = "Một trong các role, rỗng là mọi role") Set<String> roles,
    @Schema(description = "Version chống ghi đè") long version,
    @Schema(description = "Lần cập nhật UTC") Instant updatedAt,
    @Schema(description = "Người cập nhật") long updatedBy
) {}
