package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng lịch sử; chỉ INSERT/đọc/cleanup, không có API sửa sự kiện đã ghi. */
@JdbcTable(
    name = "auth_access_changes",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_access_changes_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_access_changes_time", columns = { "created_at", "id" }),
        @JdbcIndex(name = "idx_access_changes_target", columns = { "target_kind", "target_key", "created_at" }),
        @JdbcIndex(name = "idx_access_changes_actor", columns = { "actor_id", "created_at" }),
        @JdbcIndex(name = "idx_access_changes_trace", columns = { "trace_id" }),
        @JdbcIndex(name = "idx_access_changes_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_access_changes_updated_by", columns = { "updated_by" }),
    }
)
public final class AccessChange {

    private AccessChange() {}
}
