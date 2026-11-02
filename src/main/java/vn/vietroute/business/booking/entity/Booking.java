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

    @Column(name = "code", insertable = false, updatable = false)
    private String code;

    @Column(name = "trip_id", nullable = false)
    private long tripId;

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "source_code", nullable = false, length = 20)
    private String sourceCode;

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "pickup_stop_id", nullable = false)
    private long pickupStopId;

    @Column(name = "dropoff_stop_id", nullable = false)
    private long dropoffStopId;

    @Column(name = "pickup_order", nullable = false)
    private int pickupOrder;

    @Column(name = "dropoff_order", nullable = false)
    private int dropoffOrder;

    @Column(name = "seats", nullable = false)
    private int seats;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "request_key", nullable = false)
    private UUID requestKey;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount;
}
