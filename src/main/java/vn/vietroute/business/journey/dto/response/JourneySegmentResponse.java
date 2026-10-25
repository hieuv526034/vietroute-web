package vn.vietroute.business.journey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Chỗ còn tại thời điểm đọc; giữ chỗ thật chỉ khi staff xác nhận trong transaction. */
public record JourneySegmentResponse(
    @Schema(description = "Thứ tự đoạn từ điểm i đến i+1") int segmentOrder,
    @Schema(description = "Chỗ còn") int availableSeats
) {}
