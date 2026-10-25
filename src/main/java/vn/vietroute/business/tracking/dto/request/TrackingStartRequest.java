package vn.vietroute.business.tracking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TrackingStartRequest(
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version chuyến khi tài xế chủ động bật chia sẻ")
    Long tripVersion
) {}
