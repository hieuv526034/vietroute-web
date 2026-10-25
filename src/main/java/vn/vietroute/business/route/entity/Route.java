package vn.vietroute.business.route.entity;

import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata mẫu tuyến trong schema cấu hình; JDBC dùng projection riêng, không phải ORM. */
@Getter
@Setter
@JdbcTable(
    name = "carrier_routes",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_routes_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_routes_carrier_active", columns = { "carrier_id", "active", "id" }),
        @JdbcIndex(name = "idx_routes_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_routes_updated_by", columns = { "updated_by" }),
    }
)
public class Route extends BaseEntity {

    private long carrierId;
    private long scopeId;
    private String code;
    private String nameVi;
    private String nameEn;
    private String description;
    private boolean active;
}
