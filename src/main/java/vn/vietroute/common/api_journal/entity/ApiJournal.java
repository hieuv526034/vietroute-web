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

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "instance_id", length = 36, nullable = false)
    private String instanceId;

    @Column(name = "trace_id", length = 64, nullable = false)
    private String traceId;

    @Column(name = "method", length = 12, nullable = false)
    private String method;

    @Column(name = "endpoint", length = 180, nullable = false)
    private String endpoint;

    @Column(name = "controller", length = 200, nullable = false)
    private String controller;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "code", length = 16, nullable = false)
    private String code;

    @Column(name = "message_key", length = 120, nullable = false)
    private String messageKey;

    @Column(name = "diagnostic", length = 40, nullable = false)
    private String diagnostic;

    @Column(name = "sql_state", length = 5, nullable = false)
    private String sqlState;

    @Column(name = "replay_path", length = 240, nullable = false)
    private String replayPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> queryJson;

    @Column(name = "request_body", columnDefinition = "text", nullable = false)
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "text", nullable = false)
    private String responseBody;

    @Column(name = "content_kind", length = 12, nullable = false)
    private String contentKind;

    @Column(name = "request_user_id")
    private Long requestUserId;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(name = "updated_by", nullable = false)
    private long updatedBy;
}
