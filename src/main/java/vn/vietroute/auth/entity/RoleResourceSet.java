package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Marker phân biệt tập rỗng native với role legacy chưa chuyển đổi. */
@JdbcTable(
    name = "auth_role_resource_sets",
    primaryKey = { "role_code" },
    indexes = {
        @JdbcIndex(name = "auth_role_resource_sets_pkey", columns = { "role_code" }, unique = true),
        @JdbcIndex(name = "idx_auth_role_resource_sets_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_role_resource_sets_updated_by", columns = { "updated_by" }),
    }
)
public final class RoleResourceSet {

    private RoleResourceSet() {}
}
