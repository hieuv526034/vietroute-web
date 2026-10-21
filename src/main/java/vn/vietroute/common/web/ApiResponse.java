package vn.vietroute.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Envelope dùng thống nhất cho API, kể cả lỗi xuất phát từ filter bảo mật. */
@Schema(description = "Kết quả API chuẩn VietRoute")
public record ApiResponse<T>(
    @Schema(description = "Kết quả thành công") boolean success,
    @Schema(description = "Mã ổn định, không đổi theo ngôn ngữ", example = "OK000") String code,
    @Schema(description = "Key dịch", example = "success.ok") String messageKey,
    @Schema(description = "Thông báo đã dịch") String message,
    @Schema(description = "Dữ liệu; null khi lỗi") T data,
    @Schema(description = "Lỗi field; rỗng nếu không có") List<FieldViolation> errors,
    @Schema(description = "Thời điểm server trả kết quả theo UTC") Instant timestamp,
    @Schema(description = "Đường dẫn, không gồm query string") String path,
    @Schema(description = "ID tương ứng header X-Request-ID") String traceId,
    @Schema(description = "Ngôn ngữ thực tế", allowableValues = { "vi", "en" }) String locale
) {}
