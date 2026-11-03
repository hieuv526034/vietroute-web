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

/**
 * Entity đầy đủ cột carrier_vehicles; migration giữ DDL/index, repo giữ query và transaction do service quản lý.
 */
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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Vehicle extends BaseEntity {

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Biển số hiển thị.
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    // Biển số chuẩn hóa do DB sinh, dùng kiểm trùng.
    @Column(name = "plate_key", nullable = false, length = 20, insertable = false, updatable = false)
    private String plateKey;

    // ID loại xe trong category.
    @Column(name = "vehicle_type_id", nullable = false)
    private long vehicleTypeId;

    // Nhóm danh mục cố định để ràng buộc đúng loại xe.
    @Column(name = "vehicle_type", nullable = false, length = 40)
    private String vehicleType;

    // Số chỗ tối đa cho phép.
    @Column(name = "capacity", nullable = false)
    private int capacity;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng.
     */
    @Transient
    private boolean canUpdate;

    /**
     * Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng.
     */
    @Transient
    private String carrierName;

    /**
     * Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng.
     */
    @Transient
    private String vehicleTypeName;
}
