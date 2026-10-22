package vn.vietroute.business.catalog.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Danh mục mẫu có tên vi/en và audit, không chứa logic giao diện hoặc JSON. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "catalog_items",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "catalog_items_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "catalog_items_type_code_key", columns = { "type", "code" }, unique = true),
        @JdbcIndex(name = "idx_catalog_type_active_order", columns = { "type", "active", "sort_order", "id" }),
        @JdbcIndex(name = "idx_catalog_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_catalog_updated_by", columns = { "updated_by" }),
    }
)
public class CatalogItem extends BaseEntity {

    private CatalogType type;

    private String code;

    private String nameVi;

    private String nameEn;

    private int sortOrder;

    private boolean active;
}
