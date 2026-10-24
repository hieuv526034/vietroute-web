package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import vn.vietroute.auth.entity.Role;

@Schema(description = "Gán role riêng trong phạm vi; role phải đang được gán toàn cục cho đúng tài khoản, không ADMIN")
public record ScopeMembershipRequest(
    @NotNull(message = "{validation.required}")
    @Size(max = 32, message = "{validation.invalid}")
    @Schema(description = "Role có hiệu lực tại phạm vi này; không tự nhận role ở phạm vi khác")
    Set<@NotNull(message = "{validation.required}") Role> roles,
    @Schema(description = "Bật/tắt membership; tắt không xóa lịch sử") boolean active,
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "0 để tạo mới, cập nhật dùng version hiện hành >=1")
    long version
) {}
