package vn.vietroute.auth.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Quyền hệ thống; phạm vi nhà xe/chuyến phải được kiểm tra thêm tại service nghiệp vụ. */
@JdbcTable(
    name = "auth_roles",
    primaryKey = { "code" },
    indexes = {
        @JdbcIndex(name = "auth_roles_pkey", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_roles_updated_by", columns = { "updated_by" }),
    }
)
public enum Role {
    ADMIN,
    OPERATOR,
    DRIVER,
    CUSTOMER,
}
