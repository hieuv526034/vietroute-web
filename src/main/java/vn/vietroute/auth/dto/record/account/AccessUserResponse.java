package vn.vietroute.auth.dto.record.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import vn.vietroute.auth.entity.Role;

/** DTO whitelist cho màn gán role, không có password/token/lockedUntil hoặc dữ liệu credential. */
@Schema(description = "Metadata tài khoản phục vụ gán role, không chứa credential")
public record AccessUserResponse(
    @Schema(description = "ID tài khoản") long id,
    @Schema(description = "Username") String username,
    @Schema(description = "Tên hiển thị") String displayName,
    @Schema(description = "Trạng thái hoạt động") boolean active,
    @Schema(description = "Các role hiện hành") Set<Role> roles,
    @Schema(description = "Version dùng khi gán role") long version,
    @Schema(description = "Thời điểm tạo UTC") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật UTC") Instant updatedAt
) {}
