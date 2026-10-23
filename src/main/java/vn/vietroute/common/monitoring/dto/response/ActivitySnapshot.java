package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Hiện diện chia sẻ DB, online là hoạt động trong 3 phút; không phải số người thật tuyệt đối")
public record ActivitySnapshot(
    @Schema(description = "Đã bật ghi thống kê") boolean enabled,
    @Schema(description = "Số user ID phân biệt hoạt động trong 3 phút") long onlineUsers,
    @Schema(description = "Số trình duyệt hoạt động trong 3 phút") long onlineBrowsers,
    @Schema(description = "Số trình duyệt chưa đăng nhập hoạt động trong 3 phút") long onlineGuests,
    @Schema(description = "Trình duyệt đã thấy trong 24 giờ") long visitors24Hours,
    @Schema(description = "Request ghi nhận 60 phút gần nhất") long requests60Minutes,
    @Schema(description = "401/403 trong 60 phút") long denied60Minutes,
    @Schema(description = "429 trong 60 phút") long limited60Minutes,
    @Schema(description = "5xx trong 60 phút") long failures60Minutes,
    @Schema(description = "Lượt ghi bỏ lỡ tại node này kể từ startup, không phải 0 dữ liệu khi DB lỗi")
    long missedWrites,
    @Schema(description = "Thời điểm đọc DB UTC") Instant observedAt
) {}
