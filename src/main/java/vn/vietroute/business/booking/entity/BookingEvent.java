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
@Getter
@Setter
@NoArgsConstructor
public class BookingEvent {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    // ID đơn phát sinh sự kiện.
    @Column(name = "booking_id", nullable = false)
    private long bookingId;

    // ID chuyến trong carrier_trips.
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Trạng thái đơn trước thao tác.
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus;

    // Trạng thái đơn sau thao tác.
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    // Loại sự kiện nghiệp vụ.
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    // Số tiền của sự kiện thu/hoàn; 0 nếu không phải thao tác tiền.
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // Ghi chú hoặc lý do thao tác.
    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    // UUID chống ghi lặp khi gửi lại cùng một thao tác.
    @Column(name = "request_key")
    private UUID requestKey;

    // Thời điểm tạo, lưu theo UTC.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ID người tạo; tác vụ hệ thống dùng tài khoản system (0).
    @Column(name = "created_by", nullable = false)
    private long createdBy;
}
