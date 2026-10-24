package vn.vietroute.business.location.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Danh mục địa lý giữ ID/audit ổn định; không cố định danh sách đơn vị hành chính trong code. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "location_provinces",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "location_provinces_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "location_provinces_code_key", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_location_provinces_active", columns = { "active", "sort_order", "id" }),
        @JdbcIndex(name = "idx_location_provinces_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_location_provinces_updated_by", columns = { "updated_by" }),
    }
)
public class Province extends BaseEntity {

    private String code;

    private String nameVi;

    private String nameEn;

    private int sortOrder;

    private boolean active;
}
