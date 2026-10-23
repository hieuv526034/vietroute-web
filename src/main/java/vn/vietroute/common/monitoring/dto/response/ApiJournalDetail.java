package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Chỉ lưu projection đã lọc; không phải raw HTTP và không đủ để replay nguyên trạng. */
public record ApiJournalDetail(
    @Schema(description = "Metadata request") ApiJournalEntry entry,
    @Schema(description = "Node sở hữu bộ đệm") String instanceId,
    @Schema(description = "Path an toàn để debug; biến path ngoài whitelist trở thành placeholder") String replayPath,
    @Schema(description = "Query page/size/status/lang đã kiểm whitelist; query khác bị bỏ") Map<String, String> query,
    @Schema(description = "JSON request projection; chuỗi tự do/secret bị che, body không hỗ trợ bị bỏ")
    String requestBody,
    @Schema(description = "JSON response projection có code/messageKey và data lọc; không có cookie/token")
    String responseBody,
    @Schema(description = "Loại nội dung giới hạn: JSON, MULTIPART hoặc OTHER") String contentKind
) {}
