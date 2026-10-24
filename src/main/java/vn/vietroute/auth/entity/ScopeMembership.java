package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm schema/index của nền scope; DAO chỉ trả DTO whitelist, không ORM. */
@JdbcTable(
    name = "auth_scope_memberships",
    primaryKey = { "scope_id", "user_id" },
    indexes = {
        @JdbcIndex(name = "auth_scope_memberships_pkey", columns = { "scope_id", "user_id" }, unique = true),
        @JdbcIndex(name = "idx_scope_memberships_user", columns = { "user_id", "scope_id" }),
        @JdbcIndex(name = "idx_scope_memberships_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_scope_memberships_updated_by", columns = { "updated_by" }),
    }
)
public final class ScopeMembership {

    private ScopeMembership() {}
}
