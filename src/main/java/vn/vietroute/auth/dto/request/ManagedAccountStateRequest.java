package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Khóa/mở tài khoản bằng version; mọi phiên cũ đều bị thu hồi")
public record ManagedAccountStateRequest(
    @NotNull(message = "{validation.required}") @Schema(description = "true để mở, false để khóa") Boolean active,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của tài khoản", minimum = "0")
    Long version
) {}
