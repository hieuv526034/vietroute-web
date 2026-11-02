package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỗ còn tại thời điểm đọc; giữ chỗ thật chỉ khi staff xác nhận trong transaction. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JourneySegmentResponse {

    @Schema(description = "Thứ tự đoạn từ điểm i đến i+1")
    private int segmentOrder;

    @Schema(description = "Chỗ còn")
    private int availableSeats;
}
