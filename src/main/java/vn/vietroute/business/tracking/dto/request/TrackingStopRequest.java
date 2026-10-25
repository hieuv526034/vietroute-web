package vn.vietroute.business.tracking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TrackingStopRequest(
    @NotNull(message = "{validation.required}")
    @Schema(description = "Nonce consent của lần bật trên thiết bị; không thay JWT hoặc quyền chuyến")
    UUID sessionKey
) {}
