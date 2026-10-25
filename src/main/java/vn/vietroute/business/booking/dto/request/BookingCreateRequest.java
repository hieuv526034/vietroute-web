package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** WEB/PHONE, customer/actor/scope/giá do endpoint/service xác định; client không được gửi. */
public record BookingCreateRequest(
    @Min(value = 1, message = "{validation.invalid}") @Schema(description = "ID chuyến đang nhận khách") long tripId,
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm đón snapshot của chính chuyến")
    long pickupStopId,
    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm trả snapshot sau điểm đón")
    long dropoffStopId,
    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Số chỗ 1–200, server kiểm toàn bộ chặng")
    int seats,
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên người liên hệ")
    String contactName,
    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại liên hệ 6–30 ký tự cho phép")
    String contactPhone,
    @NotNull(message = "{validation.required}")
    @Schema(description = "UUID tạo một lần cho bản nháp; replay chỉ cùng actor/payload")
    UUID requestKey
) {}
