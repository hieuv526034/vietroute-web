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
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột trip_segments; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_segments",
    indexes = {
        @Index(name = "trip_segments_pkey", columnList = "trip_id, segment_order", unique = true),
        @Index(name = "idx_trip_segments_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "trip_segments",
    primaryKey = { "trip_id", "segment_order" },
    indexes = {
        @JdbcIndex(name = "trip_segments_pkey", columns = { "trip_id", "segment_order" }, unique = true),
        @JdbcIndex(name = "idx_trip_segments_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@IdClass(TripSegment.Key.class)
public class TripSegment {

    @Id
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Id
    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "reserved_seats", nullable = false)
    private int reservedSeats;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private long updatedBy;

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

        private static final long serialVersionUID = 1L;

        private long tripId;

        private int segmentOrder;
    }
}
