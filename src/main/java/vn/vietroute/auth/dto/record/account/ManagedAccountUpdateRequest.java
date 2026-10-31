package vn.vietroute.auth.dto.record.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Sửa tên hiển thị; username bất biến, email/mật khẩu theo vòng đời xác minh riêng")
public record ManagedAccountUpdateRequest(
    @Schema(description = "Tên hiển thị tài khoản")
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    String displayName,
    @Schema(description = "Version hiện tại", minimum = "0")
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    Long version
) {}
