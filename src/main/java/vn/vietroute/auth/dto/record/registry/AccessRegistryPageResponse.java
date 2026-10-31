package vn.vietroute.auth.dto.record.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.common.base.PageResponse;

/** Trang registry và quota toàn loại; allocated không thay đổi theo bộ lọc tìm kiếm. */
@Schema(description = "Trang mã role/quyền và giới hạn dung lượng cấu hình")
public record AccessRegistryPageResponse(
    @Schema(description = "Trang đã lọc tại PostgreSQL") PageResponse<AccessRegistryResponse> page,
    @Schema(description = "Số mã tối đa cấu hình cho loại này, không phải quyền toàn cục") int capacity,
    @Schema(description = "Số mã chưa xóa gồm cả mã tắt, không tính tombstone role") long allocated
) {}
