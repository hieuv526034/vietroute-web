package vn.vietroute.common.monitoring.model;

import java.time.Instant;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Dữ liệu quan sát tạm thời, userId là đối tượng được thấy, không phải audit actor do client gửi. */
@JdbcTable(
    name = "system_visitor_presence",
    primaryKey = { "visitor_hash" },
    indexes = {
        @JdbcIndex(name = "idx_presence_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_presence_updated_by", columns = { "updated_by" }),
        @JdbcIndex(name = "system_visitor_presence_pkey", columns = { "visitor_hash" }, unique = true),
        @JdbcIndex(name = "idx_presence_last_seen", columns = { "last_seen" }),
        @JdbcIndex(name = "idx_presence_user_seen", columns = { "user_id", "last_seen" }),
    }
)
public record VisitorPresence(String visitorHash, Long userId, Instant firstSeen, Instant lastSeen) {}
