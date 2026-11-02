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

/** Entity đầy đủ cột carrier_vehicles; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carrier_vehicles",
    indexes = {
        @Index(name = "carrier_vehicles_pkey", columnList = "id", unique = true),
        @Index(name = "carrier_vehicles_plate_key_key", columnList = "plate_key", unique = true),
        @Index(name = "carrier_vehicles_carrier_id_code_key", columnList = "carrier_id, code", unique = true),
        @Index(name = "idx_vehicles_carrier_state", columnList = "carrier_id, active, id"),
        @Index(name = "idx_vehicles_type", columnList = "vehicle_type_id"),
        @Index(name = "idx_vehicles_created_by", columnList = "created_by"),
        @Index(name = "idx_vehicles_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "carrier_vehicles",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_vehicles_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carrier_vehicles_plate_key_key", columns = { "plate_key" }, unique = true),
        @JdbcIndex(name = "carrier_vehicles_carrier_id_code_key", columns = { "carrier_id", "code" }, unique = true),
        @JdbcIndex(name = "idx_vehicles_carrier_state", columns = { "carrier_id", "active", "id" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_type", columns = { "vehicle_type_id" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_updated_by", columns = { "updated_by" }, unique = false),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Vehicle extends BaseEntity {

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "plate_key", nullable = false, length = 20, insertable = false, updatable = false)
    private String plateKey;

    @Column(name = "vehicle_type_id", nullable = false)
    private long vehicleTypeId;

    @Column(name = "vehicle_type", nullable = false, length = 40)
    private String vehicleType;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private boolean canUpdate;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private String carrierName;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private String vehicleTypeName;
}
