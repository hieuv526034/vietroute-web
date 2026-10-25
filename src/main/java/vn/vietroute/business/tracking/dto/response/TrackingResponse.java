package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Projection không actor/scope/nonce; vị trí chỉ hiện khi consent còn lease và viewer có quyền riêng tư. */
public record TrackingResponse(
    @Schema(description = "DISABLED/OFF/WAITING/LIVE/STALE/CLOSED/UNAVAILABLE") String status,
    @Schema(description = "Google Maps chưa cấu hình; không gửi tọa độ sang provider") String mapStatus,
    @Schema(description = "Giờ server UTC để đánh giá độ mới") Instant serverNow,
    @Schema(description = "Thời điểm nhận cuối nếu còn consent, không tự coi là giờ GPS") Instant lastReceivedAt,
    @Schema(description = "Mẫu mới nhất nếu còn lease, null khi ngừng/hết lease/không đủ điều kiện")
    TrackingPointResponse latest,
    @Schema(description = "Tối đa 100 mẫu gần nhất trong cùng consent, không phải toàn hành trình")
    List<TrackingPointResponse> trail
) {}
