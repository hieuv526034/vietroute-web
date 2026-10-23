package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm table/index; không xuất hash/token nội bộ thành DTO. */
@JdbcTable(
    name = "auth_account_challenges",
    primaryKey = { "user_id", "purpose" },
    indexes = {
        @JdbcIndex(name = "auth_account_challenges_pkey", columns = { "user_id", "purpose" }, unique = true),
        @JdbcIndex(name = "idx_auth_account_challenges_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_account_challenges_updated_by", columns = { "updated_by" }),
    }
)
public final class AccountChallenge {

    private AccountChallenge() {}
}
