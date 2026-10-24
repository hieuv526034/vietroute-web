package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;

@Schema(
    description = "Lịch sử thay đổi quyền đã commit, chỉ ADMIN được đọc; không phải audit bất biến ở mức quản trị DB"
)
public record AccessChangeResponse(
    @Schema(description = "ID sự kiện") long id,
    @Schema(description = "Loại thay đổi") Event event,
    @Schema(description = "Loại đối tượng") Target targetKind,
    @Schema(description = "Mã/ID đối tượng") String targetKey,
    @Schema(description = "Actor từ principal đã xác minh, system là 0") long actorId,
    @Schema(description = "Username join theo actor ID, không email") String actorUsername,
    @Schema(description = "Mã yêu cầu nếu thay đổi từ HTTP") String traceId,
    @Schema(description = "Thời điểm UTC") Instant createdAt,
    @Schema(description = "Trường trước thay đổi hoặc các mapping bị bỏ") AccessChangeStateResponse before,
    @Schema(description = "Trường sau thay đổi hoặc các mapping được thêm") AccessChangeStateResponse after
) {}
