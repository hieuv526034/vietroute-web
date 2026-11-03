package vn.vietroute.business.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột bookings; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "bookings",
    indexes = {
        @Index(name = "bookings_pkey", columnList = "id", unique = true),
        @Index(name = "idx_bookings_one_active_web", columnList = "customer_id", unique = true),
        @Index(name = "idx_bookings_trip_status", columnList = "trip_id, status, id"),
        @Index(name = "idx_bookings_carrier_status", columnList = "carrier_id, status, created_at, id"),
        @Index(name = "idx_bookings_customer", columnList = "customer_id, created_at, id"),
        @Index(name = "idx_bookings_pickup", columnList = "pickup_stop_id, status"),
        @Index(name = "idx_bookings_dropoff", columnList = "dropoff_stop_id, status"),
        @Index(name = "idx_bookings_source", columnList = "source_id"),
        @Index(name = "idx_bookings_created_by", columnList = "created_by"),
        @Index(name = "idx_bookings_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "bookings",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "bookings_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_bookings_one_active_web", columns = { "customer_id" }, unique = true),
        @JdbcIndex(name = "idx_bookings_trip_status", columns = { "trip_id", "status", "id" }),
        @JdbcIndex(name = "idx_bookings_carrier_status", columns = { "carrier_id", "status", "created_at", "id" }),
        @JdbcIndex(name = "idx_bookings_customer", columns = { "customer_id", "created_at", "id" }),
        @JdbcIndex(name = "idx_bookings_pickup", columns = { "pickup_stop_id", "status" }),
        @JdbcIndex(name = "idx_bookings_dropoff", columns = { "dropoff_stop_id", "status" }),
        @JdbcIndex(name = "idx_bookings_source", columns = { "source_id" }),
        @JdbcIndex(name = "idx_bookings_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_bookings_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Booking extends BaseEntity {

    // Mã đơn do DB sinh; ứng dụng chỉ đọc.
    @Column(name = "code", insertable = false, updatable = false)
    private String code;

    // ID chuyến trong carrier_trips.
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // ID khách sở hữu đơn WEB; đơn PHONE có thể null.
    @Column(name = "customer_id")
    private Long customerId;

    // Nguồn đặt WEB hoặc PHONE.
    @Column(name = "source_code", nullable = false, length = 20)
    private String sourceCode;

    // ID giá trị nguồn đặt trong category.
    @Column(name = "source_id", nullable = false)
    private long sourceId;

    // Nhóm danh mục ràng buộc giá trị nguồn đặt.
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    // ID điểm đón trong snapshot trip_stops.
    @Column(name = "pickup_stop_id", nullable = false)
    private long pickupStopId;

    // ID điểm trả trong snapshot trip_stops.
    @Column(name = "dropoff_stop_id", nullable = false)
    private long dropoffStopId;

    // Thứ tự điểm đón trên chuyến.
    @Column(name = "pickup_order", nullable = false)
    private int pickupOrder;

    // Thứ tự điểm trả, phải lớn hơn điểm đón.
    @Column(name = "dropoff_order", nullable = false)
    private int dropoffOrder;

    // Số chỗ khách đặt trên toàn bộ chặng.
    @Column(name = "seats", nullable = false)
    private int seats;

    // Giá một chỗ của chặng tại lúc đặt.
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Tổng giá đặt bằng unitPrice nhân seats, không phải tiền đã thu.
    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    // Tên người liên hệ của đơn, dữ liệu riêng tư.
    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    // Điện thoại người liên hệ, dữ liệu riêng tư.
    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    // PENDING chưa giữ chỗ; CONFIRMED giữ chỗ; COMPLETED hoặc CANCELLED kết thúc.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    // UUID chống ghi lặp khi gửi lại cùng một thao tác.
    @Column(name = "request_key", nullable = false)
    private UUID requestKey;

    // Tổng số tiền đã ghi nhận thu.
    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount;

    // Tổng số tiền đã ghi nhận hoàn, không vượt số đã thu.
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount;
}
