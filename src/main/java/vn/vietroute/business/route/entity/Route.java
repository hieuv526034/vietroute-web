package vn.vietroute.business.route.entity;

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

/** Entity đầy đủ cột carrier_routes; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carrier_routes",
    indexes = {
        @Index(name = "carrier_routes_pkey", columnList = "id", unique = true),
        @Index(name = "idx_routes_carrier_active", columnList = "carrier_id, active, id"),
        @Index(name = "idx_routes_created_by", columnList = "created_by"),
        @Index(name = "idx_routes_updated_by", columnList = "updated_by"),
    }
)
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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Route extends BaseEntity {

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 160)
    private String nameVi;

    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;
}
