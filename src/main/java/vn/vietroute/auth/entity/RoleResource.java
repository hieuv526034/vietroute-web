package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata mapping tài nguyên, không trả entity này qua API. */
@JdbcTable(
    name = "auth_role_resources",
    primaryKey = { "role_code", "kind", "target" },
    indexes = {
        @JdbcIndex(name = "auth_role_resources_pkey", columns = { "role_code", "kind", "target" }, unique = true),
        @JdbcIndex(name = "idx_auth_role_resources_resource", columns = { "kind", "target" }),
        @JdbcIndex(name = "idx_auth_role_resources_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_role_resources_updated_by", columns = { "updated_by" }),
    }
)
public final class RoleResource {

    private RoleResource() {}
}
