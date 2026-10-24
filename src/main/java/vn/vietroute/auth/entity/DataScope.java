package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm schema/index của nền scope; DAO chỉ trả DTO whitelist, không ORM. */
@JdbcTable(
    name = "auth_data_scopes",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_data_scopes_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "auth_data_scopes_code_key", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_data_scopes_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_data_scopes_updated_by", columns = { "updated_by" }),
    }
)
public final class DataScope {

    private DataScope() {}
}
