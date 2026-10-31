package vn.vietroute.auth.dto.record.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Cập nhật hồ sơ của chính principal; không nhận user ID, role, email hay audit")
public record ProfileUpdateRequest(
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên hiển thị, không để trống")
    String displayName,
    @Positive(message = "{validation.invalid}")
    @Schema(description = "ID ảnh READY thuộc chính mình; null để bỏ ảnh đại diện")
    Long avatarMediaId,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version mới nhất từ auth/me, chặn ghi đè đồng thời")
    Long version
) {}
