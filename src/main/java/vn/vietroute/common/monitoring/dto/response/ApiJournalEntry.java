package vn.vietroute.common.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Metadata chỉ gồm route đã đăng ký và mã lỗi, không có IP/account/header/raw URL. */
public record ApiJournalEntry(
    @Schema(description = "ID bản ghi do server sinh, khác traceId do client có thể cung cấp") UUID id,
    @Schema(description = "Thời điểm bắt đầu UTC") Instant timestamp,
    @Schema(description = "Mã yêu cầu dùng đối chiếu log") String traceId,
    @Schema(description = "HTTP method thuộc whitelist") String method,
    @Schema(description = "Route template MVC; UNMAPPED khi bị chặn trước controller/không có mapping") String endpoint,
    @Schema(description = "Tên class và method controller hoặc PRE_CONTROLLER") String controller,
    @Schema(description = "HTTP status thực tế") int status,
    @Schema(description = "Thời gian request trước chính thao tác ghi nhật ký, ms") long durationMs,
    @Schema(description = "Mã kết quả ổn định; UNKNOWN nếu lỗi không qua envelope") String code,
    @Schema(description = "Key thông báo vi/en, không chứa tham số nghiệp vụ") String messageKey,
    @Schema(description = "Nhóm lỗi nội bộ đã lọc; không phải stack trace") String diagnostic,
    @Schema(description = "SQLState chuẩn khi có; không có câu SQL/param hoặc JDBC URL") String sqlState
) {}
