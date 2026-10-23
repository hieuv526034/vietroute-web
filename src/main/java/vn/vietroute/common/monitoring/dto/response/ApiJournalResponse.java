package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.common.base.PageResponse;

/** Lịch sử PostgreSQL dùng chung giữa các node, không cache lịch sử trong RAM. */
public record ApiJournalResponse(
    @Schema(description = "Node phục vụ truy vấn; lịch sử DB dùng chung") String instanceId,
    @Schema(description = "Có ghi nhận request mới không") boolean enabled,
    @Schema(description = "Mục tiêu số bản ghi toàn DB; dọn theo lô, có thể vượt tạm thời") int capacity,
    @Schema(description = "Thời gian lưu tối đa, phút") int retentionMinutes,
    @Schema(description = "Lần không lưu được log từ startup trên node này") long failedWrites,
    @Schema(description = "Kho lưu lịch sử: POSTGRESQL") String storage,
    @Schema(description = "Trang kết quả mới nhất trước") PageResponse<ApiJournalEntry> records
) {}
