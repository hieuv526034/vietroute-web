package vn.vietroute.business.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.trip.constant.TripProgressAction;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.common.validation.BusinessCode;
import vn.vietroute.common.validation.RequestGroups;

/** Request dùng chung theo tài nguyên; controller chọn nhóm validation, service bảo vệ định danh/audit/phạm vi. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dữ liệu Trip; trường bắt buộc phụ thuộc thao tác, xem schema từng API")
public class TripRequest {

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @BusinessCode
    @Schema(description = "Mã chuyến bất biến, duy nhất trong nhà xe")
    private String code;

    @Min(value = 1, message = "{validation.invalid}", groups = { RequestGroups.Create.class })
    @Schema(description = "ID mẫu tuyến cùng nhà xe/phạm vi")
    private long routeId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID xe active, cùng nhà xe")
    private long vehicleId;

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "ID hồ sơ tài xế active, liên kết DRIVER ready đúng scope")
    private long driverId;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Create.class })
    @Schema(description = "Mốc đầu tuyến UTC ISO-8601; sau thời điểm hiện tại, tối đa 2 năm")
    private Instant departureAt;

    @Min(value = 1, message = "{validation.invalid}")
    @Max(value = 200, message = "{validation.invalid}")
    @Schema(description = "Chỗ bán 1–200, không vượt xe")
    private int capacity;

    @NotNull(
        message = "{validation.required}",
        groups = {
            RequestGroups.Update.class,
            RequestGroups.State.class,
            RequestGroups.Notice.class,
            RequestGroups.Progress.class,
        }
    )
    @Min(
        value = 0,
        message = "{validation.invalid}",
        groups = { Default.class, RequestGroups.State.class, RequestGroups.Notice.class, RequestGroups.Progress.class }
    )
    @Schema(description = "Version đã đọc")
    private Long version;

    @NotBlank(message = "{validation.required}", groups = { RequestGroups.Notice.class })
    @NotNull(
        message = "{validation.required}",
        groups = { RequestGroups.Update.class, RequestGroups.State.class, RequestGroups.Progress.class }
    )
    @Size(
        max = 2000,
        message = "{validation.length}",
        groups = { Default.class, RequestGroups.State.class, RequestGroups.Notice.class, RequestGroups.Progress.class }
    )
    @Schema(description = "Lý do đổi phân công")
    private String note;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.State.class })
    @Schema(description = "OPEN hoặc CANCELLED; các trạng thái khác không nhận từ staff API")
    private TripStatus status;

    @NotNull(message = "{validation.required}", groups = { RequestGroups.Progress.class })
    @Schema(description = "START/ARRIVE/DEPART/COMPLETE, backend kiểm thứ tự")
    private TripProgressAction action;

    @Min(value = 1, message = "{validation.invalid}", groups = { RequestGroups.Progress.class })
    @Max(value = 32, message = "{validation.invalid}", groups = { RequestGroups.Progress.class })
    @Schema(description = "Thứ tự điểm của chính chuyến; START=1, COMPLETE=điểm cuối")
    private int stopOrder;
}
