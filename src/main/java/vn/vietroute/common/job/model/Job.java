package vn.vietroute.common.job.model;

import java.time.Instant;
import java.util.UUID;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Bản ghi hạ tầng nội bộ, payload không được xuất qua API quản trị/log. UUID riêng với ID nghiệp vụ Long. */
@JdbcTable(
    name = "system_jobs",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "system_jobs_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "system_jobs_dedup_key_key", columns = { "dedup_key" }, unique = true),
        @JdbcIndex(name = "idx_jobs_claim", columns = { "status", "available_at", "created_at", "id" }),
        @JdbcIndex(name = "idx_jobs_expired_lease", columns = { "locked_until" }),
        @JdbcIndex(name = "idx_jobs_finished", columns = { "finished_at" }),
        @JdbcIndex(name = "idx_jobs_created_by", columns = { "created_by" }),
    }
)
public record Job(
    UUID id,
    String type,
    String payload,
    int attempts,
    int maxAttempts,
    UUID leaseToken,
    Instant lockedUntil
) {}
