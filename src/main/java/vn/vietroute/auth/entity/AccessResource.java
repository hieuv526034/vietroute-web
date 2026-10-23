package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng danh bạ; projection trả API được khai báo tường minh ở AccessResourceResponse. */
@JdbcTable(
    name = "auth_access_resources",
    primaryKey = { "kind", "target" },
    indexes = {
        @JdbcIndex(name = "pk_auth_access_resources", columns = { "kind", "target" }, unique = true),
        @JdbcIndex(name = "idx_auth_access_resources_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_access_resources_updated_by", columns = { "updated_by" }),
    }
)
public final class AccessResource {

    private AccessResource() {}
}
