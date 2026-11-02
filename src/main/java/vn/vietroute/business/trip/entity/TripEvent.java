package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột trip_events; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_events",
    indexes = {
        @Index(name = "trip_events_pkey", columnList = "id", unique = true),
        @Index(name = "idx_trip_events_trip", columnList = "trip_id, id"),
        @Index(name = "idx_trip_events_created_by", columnList = "created_by"),
    }
)
@JdbcTable(
    name = "trip_events",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "trip_events_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trip_events_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_trip_events_created_by", columns = { "created_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TripEvent {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    // ID chuyến trong carrier_trips.
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Số thứ tự điểm dừng trên hành trình.
    @Column(name = "stop_order")
    private Integer stopOrder;

    // Loại sự kiện nghiệp vụ.
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    // Ghi chú hoặc lý do thao tác.
    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    // Thời điểm tạo, lưu theo UTC.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ID người tạo; tác vụ hệ thống dùng tài khoản system (0).
    @Column(name = "created_by", nullable = false)
    private long createdBy;
}
