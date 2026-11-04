package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entity đầy đủ cột trip_segments; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_segments",
    indexes = {
        @Index(name = "trip_segments_pkey", columnList = "trip_id, segment_order", unique = true),
        @Index(name = "idx_trip_segments_updated_by", columnList = "updated_by"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@IdClass(TripSegment.Key.class)
public class TripSegment {

    // ID chuyến trong carrier_trips.
    @Id
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Đoạn nối điểm thứ n với n+1; là thành phần khóa chính cùng tripId.
    @Id
    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    // Số chỗ tối đa cho phép.
    @Column(name = "capacity", nullable = false)
    private int capacity;

    // Số chỗ đã giữ trên đoạn; không tính đơn PENDING.
    @Column(name = "reserved_seats", nullable = false)
    private int reservedSeats;

    // Thời điểm cập nhật gần nhất, lưu theo UTC.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ID người sửa gần nhất; tác vụ hệ thống dùng system (0).
    @Column(name = "updated_by", nullable = false)
    private long updatedBy;

    // Phiên bản khóa lạc quan; từ chối cập nhật từ dữ liệu đã cũ.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Khóa ghép thật của bảng; không thêm identity/migration chỉ để rút gọn lớp. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        // Phiên bản định dạng khóa ghép khi Java serialize.
        private static final long serialVersionUID = 1L;

        // ID chuyến, thành phần thứ nhất của khóa ghép.
        private long tripId;

        // Thứ tự đoạn, thành phần thứ hai của khóa ghép.
        private int segmentOrder;
    }
}
