package vn.vietroute.common.monitoring.model;

import java.time.Instant;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Bộ đếm theo chủ thể/phút; không lưu danh sách request, URL, header hoặc nội dung cá nhân. */
@JdbcTable(
    name = "system_activity_buckets",
    primaryKey = { "subject_hash", "bucket_at" },
    indexes = {
        @JdbcIndex(name = "idx_activity_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_activity_updated_by", columns = { "updated_by" }),
        @JdbcIndex(name = "system_activity_buckets_pkey", columns = { "subject_hash", "bucket_at" }, unique = true),
        @JdbcIndex(name = "idx_activity_bucket_at", columns = { "bucket_at" }),
        @JdbcIndex(name = "idx_activity_user_bucket", columns = { "user_id", "bucket_at" }),
    }
)
public record ActivityBucket(
    String subjectHash,
    Instant bucketAt,
    Long userId,
    long requests,
    long denied,
    long limited,
    long failures
) {}
