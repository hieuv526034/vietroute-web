package vn.vietroute.business.tracking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ trả nonce cho tài xế sau POST bật; không lưu browser storage hoặc trả qua API viewer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrackingSessionResponse {

    @Schema(description = "Nonce consent mới, thu hồi nonce cũ khi bật lại")
    private UUID sessionKey;

    @Schema(description = "Thời điểm bắt đầu UTC")
    private Instant startedAt;

    @Schema(description = "Tần suất gửi tối thiểu, giây")
    private int minSampleSeconds;

    @Schema(description = "Lease không có mẫu mới hết hiệu lực sau số giây này")
    private int leaseSeconds;
}
