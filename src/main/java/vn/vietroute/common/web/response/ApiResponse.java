package vn.vietroute.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Envelope dùng thống nhất cho API, kể cả lỗi xuất phát từ filter bảo mật. */
@Schema(description = "Kết quả API chuẩn VietRoute")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiResponse<T> {

    @Schema(description = "Kết quả thành công")
    private final boolean success;

    @Schema(description = "Mã ổn định, không đổi theo ngôn ngữ", example = "OK000")
    private final String code;

    @Schema(description = "Key dịch", example = "success.ok")
    private final String messageKey;

    @Schema(description = "Thông báo đã dịch")
    private final String message;

    @Schema(description = "Dữ liệu; null khi lỗi")
    private final T data;

    @Schema(description = "Lỗi field; rỗng nếu không có")
    private final List<FieldViolation> errors;

    @Schema(description = "Thời điểm server trả kết quả theo UTC")
    private final Instant timestamp;

    @Schema(description = "Đường dẫn, không gồm query string")
    private final String path;

    @Schema(description = "ID tương ứng header X-Request-ID")
    private final String traceId;

    @Schema(description = "Ngôn ngữ thực tế", allowableValues = { "vi", "en" })
    private final String locale;
}
