package vn.vietroute.business.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.booking.constant.BookingPaymentKind;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Booking; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class BookingRequest extends BaseRequest {

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID chuyến đang nhận khách")
    private long tripId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm đón snapshot của chính chuyến")
    private long pickupStopId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID điểm trả snapshot sau điểm đón")
    private long dropoffStopId;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Số chỗ 1–200, server kiểm toàn bộ chặng")
    private int seats;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tên người liên hệ")
    private String contactName;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.PHONE_NUMBER, message = "{validation.invalid}")
    @Schema(description = "Điện thoại liên hệ 6–30 ký tự cho phép")
    private String contactPhone;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class, RequestGroups.Payment.class })
    @Schema(description = "UUID tạo một lần cho bản nháp; replay chỉ cùng actor/payload")
    private UUID requestKey;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.State.class })
    @Schema(description = "Trạng thái đích, server kiểm theo endpoint/vai trò")
    private BookingStatus status;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.State.class, RequestGroups.Payment.class })
    @Min(
        value = 0,
        message = "{validation.invalid}",
        groups = { Default.class, RequestGroups.State.class, RequestGroups.Payment.class }
    )
    @Schema(description = "Version đơn đã đọc")
    private Long version;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Payment.class })
    @NotNull(message = "{validation.required}", groups = { RequestGroups.State.class })
    @Size(
        max = 2000,
        message = "{validation.length}",
        groups = { RequestGroups.State.class, RequestGroups.Payment.class }
    )
    @Schema(description = "Lý do; bắt buộc khi hủy/từ chối")
    private String note;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Payment.class })
    @Schema(description = "PAYMENT hoặc REFUND")
    private BookingPaymentKind kind;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Payment.class })
    @DecimalMin(value = "0.01", message = "{validation.invalid}", groups = { RequestGroups.Payment.class })
    @Digits(integer = 13, fraction = 2, message = "{validation.invalid}", groups = { RequestGroups.Payment.class })
    @Schema(description = "Số tiền đã thu/hoàn thực tế VND, tối đa còn phải thu/hoàn")
    private BigDecimal amount;
}
