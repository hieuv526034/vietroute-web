package vn.vietroute.common.api_journal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import vn.vietroute.common.base.PageResponse;

/** Lịch sử PostgreSQL dùng chung giữa các node, không cache lịch sử trong RAM. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiJournalResponse {

    @Schema(description = "Node phục vụ truy vấn; lịch sử DB dùng chung")
    private final String instanceId;

    @Schema(description = "Có ghi nhận request mới không")
    private final boolean enabled;

    @Schema(description = "Mục tiêu số bản ghi toàn DB; dọn theo lô, có thể vượt tạm thời")
    private final int capacity;

    @Schema(description = "Thời gian lưu tối đa, phút")
    private final int retentionMinutes;

    @Schema(description = "Lần không lưu được log từ startup trên node này")
    private final long failedWrites;

    @Schema(description = "Kho lưu lịch sử: POSTGRESQL")
    private final String storage;

    @Schema(description = "Trang kết quả mới nhất trước")
    private final PageResponse<ApiJournalEntry> records;
}
