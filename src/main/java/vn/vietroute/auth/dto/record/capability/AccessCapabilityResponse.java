package vn.vietroute.auth.dto.record.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "Quyền giao diện hiệu lực theo DB, guard HTTP và policy; không thay kiểm quyền backend")
public record AccessCapabilityResponse(
    @Schema(description = "Mã chức năng") String function,
    @Schema(description = "Được mở trang; nhóm không có trang trả false") boolean view,
    @Schema(description = "Hành động được phép trên các endpoint hiện có") Set<String> actions,
    @Schema(description = "Endpoint được phép, kể cả policy siết riêng từng route") Set<String> targets
) {}
