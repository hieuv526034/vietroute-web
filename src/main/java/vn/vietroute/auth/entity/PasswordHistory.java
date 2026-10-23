package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm table/index; không xuất hash/token nội bộ thành DTO. */
@JdbcTable(
    name = "auth_password_history",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_password_history_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_auth_password_history_user", columns = { "user_id", "id" }),
        @JdbcIndex(name = "idx_auth_password_history_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_password_history_updated_by", columns = { "updated_by" }),
    }
)
public final class PasswordHistory {

    private PasswordHistory() {}
}
