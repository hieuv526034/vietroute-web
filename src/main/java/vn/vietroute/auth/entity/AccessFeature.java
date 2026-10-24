package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata cây lưu PostgreSQL; định nghĩa server mới quyết định guard, không lấy public/permission do client gửi. */
@JdbcTable(
    name = "auth_access_functions",
    primaryKey = { "code" },
    indexes = {
        @JdbcIndex(name = "auth_access_functions_pkey", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_access_functions_parent", columns = { "parent_code" }),
        @JdbcIndex(name = "idx_access_functions_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_access_functions_updated_by", columns = { "updated_by" }),
        @JdbcIndex(name = "idx_access_functions_view", columns = { "view_permission" }),
    }
)
public final class AccessFeature {

    private AccessFeature() {}
}
