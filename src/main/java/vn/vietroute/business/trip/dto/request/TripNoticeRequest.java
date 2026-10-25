package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Thông báo sự cố không đổi trạng thái chuyến hoặc giả lập hoàn tiền/chuyển khách. */
public record TripNoticeRequest(
    @NotBlank(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.length}")
    @Schema(description = "Thông báo vận hành và thông tin liên hệ xử lý")
    String note,
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version chuyến vừa đọc")
    Long version
) {}
