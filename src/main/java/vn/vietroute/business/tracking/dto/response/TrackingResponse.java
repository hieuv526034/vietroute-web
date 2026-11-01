package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Projection không actor/scope/nonce; vị trí chỉ hiện khi consent còn lease và viewer có quyền riêng tư. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrackingResponse {

    @Schema(description = "DISABLED/OFF/WAITING/LIVE/STALE/CLOSED/UNAVAILABLE")
    private String status;

    @Schema(description = "Google Maps chưa cấu hình; không gửi tọa độ sang provider")
    private String mapStatus;

    @Schema(description = "Giờ server UTC để đánh giá độ mới")
    private Instant serverNow;

    @Schema(description = "Thời điểm nhận cuối nếu còn consent, không tự coi là giờ GPS")
    private Instant lastReceivedAt;

    @Schema(description = "Mẫu mới nhất nếu còn lease, null khi ngừng/hết lease/không đủ điều kiện")
    private TrackingPointResponse latest;

    @Schema(description = "Tối đa 100 mẫu gần nhất trong cùng consent, không phải toàn hành trình")
    private List<TrackingPointResponse> trail;
}
