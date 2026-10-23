package vn.vietroute.common.monitoring.model;

import java.time.Instant;
import java.util.UUID;
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
public record ApiJournalRecord(UUID id, Instant recordedAt, Instant startedAt, String instanceId, String traceId) {}
