package vn.vietroute.auth.dto.response.history;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;

@Schema(
    description = "Lịch sử thay đổi quyền đã commit, chỉ ADMIN được đọc; không phải audit bất biến ở mức quản trị DB"
)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessChangeResponse {

    @Schema(description = "ID sự kiện")
    private long id;

    @Schema(description = "Loại thay đổi")
    private Event event;

    @Schema(description = "Loại đối tượng")
    private Target targetKind;

    @Schema(description = "Mã/ID đối tượng")
    private String targetKey;

    @Schema(description = "Actor từ principal đã xác minh, system là 0")
    private long actorId;

    @Schema(description = "Username join theo actor ID, không email")
    private String actorUsername;

    @Schema(description = "Mã yêu cầu nếu thay đổi từ HTTP")
    private String traceId;

    @Schema(description = "Thời điểm UTC")
    private Instant createdAt;

    @Schema(description = "Trường trước thay đổi hoặc các mapping bị bỏ")
    private AccessChangeStateResponse before;

    @Schema(description = "Trường sau thay đổi hoặc các mapping được thêm")
    private AccessChangeStateResponse after;

    public AccessChangeResponse(
        long id,
        Event event,
        Target targetKind,
        String targetKey,
        long actorId,
        String actorUsername,
        String traceId,
        Instant createdAt,
        AccessChangeStateResponse before,
        AccessChangeStateResponse after
    ) {
        this.id = id;
        this.event = event;
        this.targetKind = targetKind;
        this.targetKey = targetKey;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.before = before;
        this.after = after;
    }

    public long id() {
        return id;
    }

    public Event event() {
        return event;
    }

    public Target targetKind() {
        return targetKind;
    }

    public String targetKey() {
        return targetKey;
    }

    public long actorId() {
        return actorId;
    }

    public String actorUsername() {
        return actorUsername;
    }

    public String traceId() {
        return traceId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AccessChangeStateResponse before() {
        return before;
    }

    public AccessChangeStateResponse after() {
        return after;
    }
}
