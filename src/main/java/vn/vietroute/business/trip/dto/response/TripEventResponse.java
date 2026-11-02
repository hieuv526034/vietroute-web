package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sự kiện vận hành không có danh tính/email actor hoặc payload gốc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TripEventResponse {

    @Schema(description = "ID sự kiện")
    private long id;

    @Schema(description = "Loại sự kiện")
    private String eventType;

    @Schema(description = "Thứ tự điểm, null nếu sự kiện toàn chuyến")
    private Integer stopOrder;

    @Schema(description = "Thông báo do nhà xe khai báo")
    private String note;

    @Schema(description = "Thời điểm server UTC")
    private Instant createdAt;
}
