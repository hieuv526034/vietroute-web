package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.common.base.PageResponse;

/** Snapshot phân trang trong RAM; mất khi restart, không tổng hợp toàn cụm. */
public record ApiJournalResponse(
    @Schema(description = "Node hiện tại; dùng để phát hiện load balancer đổi node") String instanceId,
    @Schema(description = "Có ghi nhận request mới không") boolean enabled,
    @Schema(description = "Số bản ghi tối đa mỗi node") int capacity,
    @Schema(description = "Thời gian lưu tối đa, phút") int retentionMinutes,
    @Schema(description = "Số bản ghi đã loại vì TTL/dung lượng từ startup") long evicted,
    @Schema(description = "Trang kết quả mới nhất trước") PageResponse<ApiJournalEntry> records
) {}
