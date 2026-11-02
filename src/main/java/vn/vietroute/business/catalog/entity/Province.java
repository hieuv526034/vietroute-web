package vn.vietroute.business.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột location_provinces; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "location_provinces",
    indexes = {
        @Index(name = "location_provinces_pkey", columnList = "id", unique = true),
        @Index(name = "location_provinces_code_key", columnList = "code", unique = true),
        @Index(name = "idx_location_provinces_active", columnList = "active, sort_order, id"),
        @Index(name = "idx_location_provinces_created_by", columnList = "created_by"),
        @Index(name = "idx_location_provinces_updated_by", columnList = "updated_by"),
    }
)
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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Province extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 120)
    private String nameVi;

    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
