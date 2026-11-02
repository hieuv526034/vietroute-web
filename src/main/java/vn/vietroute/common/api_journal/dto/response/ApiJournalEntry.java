package vn.vietroute.common.api_journal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Metadata chỉ gồm route đã đăng ký và mã lỗi, không có IP/email/header/raw URL; danh tính giới hạn ID/username đã xác thực. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiJournalEntry {

    @Schema(description = "ID bản ghi do server sinh, khác traceId do client có thể cung cấp")
    private final UUID id;

    @Schema(description = "Thời điểm bắt đầu UTC")
    private final Instant timestamp;

    @Schema(description = "Mã yêu cầu dùng đối chiếu log")
    private final String traceId;

    @Schema(description = "HTTP method thuộc whitelist")
    private final String method;

    @Schema(description = "Route template MVC; UNMAPPED khi bị chặn trước controller/không có mapping")
    private final String endpoint;

    @Schema(description = "Tên class và method controller hoặc PRE_CONTROLLER")
    private final String controller;

    @Schema(description = "HTTP status thực tế")
    private final int status;

    @Schema(description = "Thời gian request trước chính thao tác ghi nhật ký, ms")
    private final long durationMs;

    @Schema(description = "Mã kết quả ổn định; UNKNOWN nếu lỗi không qua envelope")
    private final String code;

    @Schema(description = "Key thông báo vi/en, không chứa tham số nghiệp vụ")
    private final String messageKey;

    @Schema(description = "Nhóm lỗi nội bộ đã lọc; không phải stack trace")
    private final String diagnostic;

    @Schema(description = "SQLState chuẩn khi có; không có câu SQL/param hoặc JDBC URL")
    private final String sqlState;

    @Schema(description = "ID user được JWT/password xác minh; null là khách hoặc lịch sử cũ chưa ghi nhận")
    private final Long userId;

    @Schema(description = "Username đọc từ auth_users theo ID, chỉ người được cấp endpoint nhật ký được xem")
    private final String username;
}
