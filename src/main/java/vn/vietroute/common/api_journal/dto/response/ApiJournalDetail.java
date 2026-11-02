package vn.vietroute.common.api_journal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Chỉ lưu projection đã lọc; không phải raw HTTP và không đủ để replay nguyên trạng. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiJournalDetail {

    @Schema(description = "Metadata request")
    private final ApiJournalEntry entry;

    @Schema(description = "Node đã ghi request; tra được từ mọi node cùng DB/schema")
    private final String instanceId;

    @Schema(description = "Path an toàn để debug; biến path ngoài whitelist trở thành placeholder")
    private final String replayPath;

    @Schema(description = "Query page/size/status/lang đã kiểm whitelist; query khác bị bỏ")
    private final Map<String, String> query;

    @Schema(description = "JSON request projection; chuỗi tự do/secret bị che, body không hỗ trợ bị bỏ")
    private final String requestBody;

    @Schema(description = "JSON response projection có code/messageKey và data lọc; không có cookie/token")
    private final String responseBody;

    @Schema(description = "Loại nội dung giới hạn: JSON, MULTIPART hoặc OTHER")
    private final String contentKind;
}
