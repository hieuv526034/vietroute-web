package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Trình duyệt online, chỉ quản trị có quyền hệ thống được đọc")
public record OnlineVisitorResponse(
    @Schema(description = "ID user đã xác thực, null là khách") Long userId,
    @Schema(description = "Username, null là khách") String username,
    @Schema(description = "Dấu định danh băm rút gọn, không phải IP hoặc token") String visitor,
    @Schema(description = "Lần thấy đầu tiên") Instant firstSeen,
    @Schema(description = "Lần hoạt động gần nhất") Instant lastSeen
) {}
