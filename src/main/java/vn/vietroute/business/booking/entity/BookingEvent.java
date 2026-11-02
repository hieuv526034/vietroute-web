package vn.vietroute.business.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột booking_events; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "booking_events",
    indexes = {
        @Index(name = "booking_events_pkey", columnList = "id", unique = true),
        @Index(name = "idx_booking_events_parent", columnList = "booking_id, id"),
        @Index(name = "idx_booking_events_trip", columnList = "trip_id, id"),
        @Index(name = "idx_booking_events_created_by", columnList = "created_by"),
    }
)
@JdbcTable(
    name = "booking_events",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "booking_events_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_booking_events_parent", columns = { "booking_id", "id" }),
        @JdbcIndex(name = "idx_booking_events_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_booking_events_created_by", columns = { "created_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
public class BookingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "booking_id", nullable = false)
    private long bookingId;

    @Column(name = "trip_id", nullable = false)
    private long tripId;

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    @Column(name = "request_key")
    private UUID requestKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private long createdBy;
}
