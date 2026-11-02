package vn.vietroute.common.api_journal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata nhật ký; recordedAt do DB cấp, audit actor system tách khỏi request_user_id của người gọi đã xác thực. */
@JdbcTable(
    name = "api_request_journal",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "api_request_journal_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_api_journal_recorded", columns = { "recorded_at", "id" }),
        @JdbcIndex(name = "idx_api_journal_trace", columns = { "trace_id", "recorded_at" }),
        @JdbcIndex(name = "idx_api_journal_status", columns = { "http_status", "recorded_at" }),
        @JdbcIndex(name = "idx_api_journal_request_user", columns = { "request_user_id", "recorded_at" }),
        @JdbcIndex(name = "idx_api_journal_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_api_journal_updated_by", columns = { "updated_by" }),
    }
)
@Entity
@Table(
    name = "api_request_journal",
    indexes = {
        @Index(name = "idx_api_journal_recorded", columnList = "recorded_at,id"),
        @Index(name = "idx_api_journal_trace", columnList = "trace_id,recorded_at"),
        @Index(name = "idx_api_journal_status", columnList = "http_status,recorded_at"),
        @Index(name = "idx_api_journal_request_user", columnList = "request_user_id,recorded_at"),
        @Index(name = "idx_api_journal_created_by", columnList = "created_by"),
        @Index(name = "idx_api_journal_updated_by", columnList = "updated_by"),
    }
)
@Getter
@Setter
public class ApiJournal {

    // UUID định danh dòng nhật ký.
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // Thời điểm DB nhận log, dùng phân trang và retention.
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    // Thời điểm request bắt đầu tại ứng dụng.
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // ID node ghi log để phân biệt các tiến trình.
    @Column(name = "instance_id", length = 36, nullable = false)
    private String instanceId;

    // Mã đối chiếu request với nhật ký và phản hồi HTTP.
    @Column(name = "trace_id", length = 64, nullable = false)
    private String traceId;

    // HTTP method đã chuẩn hóa.
    @Column(name = "method", length = 12, nullable = false)
    private String method;

    // Mẫu endpoint đã được nhận diện, không lấy URI tùy ý.
    @Column(name = "endpoint", length = 180, nullable = false)
    private String endpoint;

    // Handler MVC đã nhận request.
    @Column(name = "controller", length = 200, nullable = false)
    private String controller;

    // HTTP status cuối cùng của phản hồi.
    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    // Thời gian xử lý request, đơn vị mili giây.
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    // Mã kết quả trong envelope API.
    @Column(name = "code", length = 16, nullable = false)
    private String code;

    // Key thông báo để tra bundle vi/en.
    @Column(name = "message_key", length = 120, nullable = false)
    private String messageKey;

    // Mã chẩn đoán đã lọc, không giữ exception thô.
    @Column(name = "diagnostic", length = 40, nullable = false)
    private String diagnostic;

    // SQLSTATE đã lọc, không chứa SQL hoặc tham số truy vấn.
    @Column(name = "sql_state", length = 5, nullable = false)
    private String sqlState;

    // Đường dẫn an toàn dùng tạo bản cURL/Postman.
    @Column(name = "replay_path", length = 240, nullable = false)
    private String replayPath;

    // Query đã qua whitelist; loại bỏ field nhạy cảm.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> queryJson;

    // Snapshot request đã lọc, không phải body thô.
    @Column(name = "request_body", columnDefinition = "text", nullable = false)
    private String requestBody;

    // Snapshot response đã lọc, không phải body thô.
    @Column(name = "response_body", columnDefinition = "text", nullable = false)
    private String responseBody;

    // Loại nội dung snapshot để hiển thị/export.
    @Column(name = "content_kind", length = 12, nullable = false)
    private String contentKind;

    // ID người gọi đã được server xác thực; null nếu chưa xác thực.
    @Column(name = "request_user_id")
    private Long requestUserId;

    // Actor hệ thống ghi nhật ký, luôn là system (0).
    @Column(name = "created_by", nullable = false)
    private long createdBy;

    // Actor hệ thống của nhật ký, luôn là system (0).
    @Column(name = "updated_by", nullable = false)
    private long updatedBy;
}
