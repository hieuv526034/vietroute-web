package vn.vietroute.common.monitoring.model;

import java.time.Instant;
import java.util.UUID;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Định danh/audit log bất biến; recordedAt do PostgreSQL cấp, không FK tới tài khoản hoặc PII. */
@JdbcTable(
    name = "api_request_journal",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "api_request_journal_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_api_journal_recorded", columns = { "recorded_at", "id" }),
        @JdbcIndex(name = "idx_api_journal_trace", columns = { "trace_id", "recorded_at" }),
        @JdbcIndex(name = "idx_api_journal_status", columns = { "http_status", "recorded_at" }),
    }
)
public record ApiJournalRecord(UUID id, Instant recordedAt, Instant startedAt, String instanceId, String traceId) {}
