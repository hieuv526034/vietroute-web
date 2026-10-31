package vn.vietroute.auth.dto.record.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Membership và role riêng tại scope; không chứa mật khẩu/email/token")
public record ScopeMembershipResponse(
    @Schema(description = "ID scope") long scopeId,
    @Schema(description = "ID tài khoản được gán") long userId,
    @Schema(description = "Tên đăng nhập join theo userId, chỉ khu quản trị") String username,
    @Schema(description = "Membership bật; role/quyền/account/scope vẫn cần active khi thao tác") boolean active,
    @Schema(description = "Các mapping role riêng trong scope, gồm role đang tắt để admin thu hồi") Set<Role> roles,
    @Schema(description = "Version chống ghi đè") long version,
    @Schema(description = "Thời điểm cập nhật UTC") Instant updatedAt,
    @Schema(description = "Actor cập nhật") long updatedBy
) {}
