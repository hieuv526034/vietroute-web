package vn.vietroute.business.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột catalog_items; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "catalog_items",
    indexes = {
        @Index(name = "catalog_items_pkey", columnList = "id", unique = true),
        @Index(name = "catalog_items_type_code_key", columnList = "type, code", unique = true),
        @Index(name = "idx_catalog_type_active_order", columnList = "type, active, sort_order, id"),
        @Index(name = "idx_catalog_created_by", columnList = "created_by"),
        @Index(name = "idx_catalog_updated_by", columnList = "updated_by"),
    }
)
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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CatalogItem extends BaseEntity {

    // Nhóm danh mục, kết hợp với code để xác định một giá trị.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CatalogType type;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", nullable = false, length = 120)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    // Thứ tự hiển thị tăng dần.
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active", nullable = false)
    private boolean active;
}
