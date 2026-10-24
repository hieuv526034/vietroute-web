package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm schema/index của nền scope; DAO chỉ trả DTO whitelist, không ORM. */
@JdbcTable(
    name = "auth_scope_roles",
    primaryKey = { "scope_id", "user_id", "role_code" },
    indexes = {
        @JdbcIndex(name = "auth_scope_roles_pkey", columns = { "scope_id", "user_id", "role_code" }, unique = true),
        @JdbcIndex(name = "idx_scope_roles_user_role", columns = { "user_id", "role_code" }),
        @JdbcIndex(name = "idx_scope_roles_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_scope_roles_updated_by", columns = { "updated_by" }),
    }
)
public final class ScopeRole {

    private ScopeRole() {}
}
