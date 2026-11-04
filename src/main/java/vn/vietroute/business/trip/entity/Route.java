package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;

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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Route extends BaseEntity {

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", nullable = false, length = 160)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    // Mô tả bổ sung.
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active", nullable = false)
    private boolean active;
}
