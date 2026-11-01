package vn.vietroute.business.carrier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột carrier_drivers; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carrier_drivers",
    indexes = {
        @Index(name = "carrier_drivers_pkey", columnList = "id", unique = true),
        @Index(name = "carrier_drivers_user_id_key", columnList = "user_id", unique = true),
        @Index(name = "carrier_drivers_carrier_id_code_key", columnList = "carrier_id, code", unique = true),
        @Index(name = "idx_drivers_carrier_state", columnList = "carrier_id, active, id"),
        @Index(name = "idx_drivers_created_by", columnList = "created_by"),
        @Index(name = "idx_drivers_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "carrier_drivers",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_drivers_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carrier_drivers_user_id_key", columns = { "user_id" }, unique = true),
        @JdbcIndex(name = "carrier_drivers_carrier_id_code_key", columns = { "carrier_id", "code" }, unique = true),
        @JdbcIndex(name = "idx_drivers_carrier_state", columns = { "carrier_id", "active", "id" }, unique = false),
        @JdbcIndex(name = "idx_drivers_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_drivers_updated_by", columns = { "updated_by" }, unique = false),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Driver extends BaseEntity {

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private boolean canUpdate;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private boolean canAssign;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private String carrierName;
}
