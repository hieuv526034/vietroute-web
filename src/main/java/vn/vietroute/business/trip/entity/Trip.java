package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột carrier_trips; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carrier_trips",
    indexes = {
        @Index(name = "carrier_trips_pkey", columnList = "id", unique = true),
        @Index(name = "idx_trips_carrier_status", columnList = "carrier_id, status, departure_at, id"),
        @Index(name = "idx_trips_vehicle_schedule", columnList = "vehicle_id, departure_at, end_at"),
        @Index(name = "idx_trips_driver_schedule", columnList = "driver_id, departure_at, end_at"),
        @Index(name = "idx_trips_driver_user", columnList = "driver_user_id, status, departure_at"),
        @Index(name = "idx_trips_report_departure", columnList = "departure_at, carrier_id, id, scope_id, status"),
        @Index(name = "idx_trips_report_carrier_date", columnList = "carrier_id, departure_at, id, scope_id, status"),
        @Index(name = "idx_trips_route", columnList = "route_id"),
        @Index(name = "idx_trips_created_by", columnList = "created_by"),
        @Index(name = "idx_trips_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "carrier_trips",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_trips_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trips_carrier_status", columns = { "carrier_id", "status", "departure_at", "id" }),
        @JdbcIndex(name = "idx_trips_vehicle_schedule", columns = { "vehicle_id", "departure_at", "end_at" }),
        @JdbcIndex(name = "idx_trips_driver_schedule", columns = { "driver_id", "departure_at", "end_at" }),
        @JdbcIndex(name = "idx_trips_driver_user", columns = { "driver_user_id", "status", "departure_at" }),
        @JdbcIndex(
            name = "idx_trips_report_departure",
            columns = { "departure_at", "carrier_id", "id", "scope_id", "status" }
        ),
        @JdbcIndex(
            name = "idx_trips_report_carrier_date",
            columns = { "carrier_id", "departure_at", "id", "scope_id", "status" }
        ),
        @JdbcIndex(name = "idx_trips_route", columns = { "route_id" }),
        @JdbcIndex(name = "idx_trips_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trips_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Trip extends BaseEntity {

    // Nhóm danh mục cố định do DB sinh, bảo vệ FK không trỏ nhầm loại.
    @Column(name = "category_vehicle_ref", insertable = false, updatable = false, length = 64)
    private String categoryVehicleRef;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // ID tuyến mẫu trong carrier_routes.
    @Column(name = "route_id", nullable = false)
    private long routeId;

    // ID xe được phân công cho chuyến.
    @Column(name = "vehicle_id", nullable = false)
    private long vehicleId;

    // ID hồ sơ tài xế được phân công.
    @Column(name = "driver_id", nullable = false)
    private long driverId;

    // Snapshot ID tài khoản tài xế, dùng kiểm quyền chuyến được giao.
    @Column(name = "driver_user_id", nullable = false)
    private long driverUserId;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Snapshot tên tuyến tiếng Việt lúc tạo chuyến.
    @Column(name = "name_vi", nullable = false, length = 160)
    private String nameVi;

    // Snapshot tên tuyến tiếng Anh lúc tạo chuyến.
    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    // Snapshot biển số xe của chuyến.
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    // Snapshot ID loại xe.
    @Column(name = "vehicle_type_id", nullable = false)
    private long vehicleTypeId;

    // Snapshot tên loại xe tiếng Việt.
    @Column(name = "vehicle_type_name_vi", nullable = false, length = 120)
    private String vehicleTypeNameVi;

    // Snapshot tên loại xe tiếng Anh.
    @Column(name = "vehicle_type_name_en", nullable = false, length = 120)
    private String vehicleTypeNameEn;

    // Snapshot tên tài xế của chuyến.
    @Column(name = "driver_name", nullable = false, length = 120)
    private String driverName;

    // Snapshot số điện thoại tài xế.
    @Column(name = "driver_phone", nullable = false, length = 30)
    private String driverPhone;

    // Giờ khởi hành dự kiến ở điểm đầu, lưu UTC.
    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    // Giờ kết thúc dự kiến ở điểm cuối, lưu UTC.
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    // Snapshot số chỗ của chuyến; không đổi theo xe sau đó.
    @Column(name = "capacity", nullable = false)
    private int capacity;

    // Vòng đời DRAFT, OPEN, IN_PROGRESS, COMPLETED hoặc CANCELLED.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    // Ghi chú điều hành hoặc lý do thay đổi trạng thái chuyến.
    @Column(name = "operation_note", nullable = false, length = 2000)
    private String operationNote;
}
