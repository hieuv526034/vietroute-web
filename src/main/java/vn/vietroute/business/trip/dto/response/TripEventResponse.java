package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Sự kiện vận hành không có danh tính/email actor hoặc payload gốc. */
public record TripEventResponse(
    @Schema(description = "ID sự kiện") long id,
    @Schema(description = "Loại sự kiện") String eventType,
    @Schema(description = "Thứ tự điểm, null nếu sự kiện toàn chuyến") Integer stopOrder,
    @Schema(description = "Thông báo do nhà xe khai báo") String note,
    @Schema(description = "Thời điểm server UTC") Instant createdAt
) {}
