package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột trip_stops; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_stops",
    indexes = {
        @Index(name = "trip_stops_pkey", columnList = "id", unique = true),
        @Index(name = "idx_trip_stops_search", columnList = "location_id, scheduled_departure_at, trip_id"),
        @Index(name = "idx_trip_stops_province_search", columnList = "province_id, scheduled_departure_at, trip_id"),
        @Index(name = "idx_trip_stops_created_by", columnList = "created_by"),
        @Index(name = "idx_trip_stops_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "trip_stops",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "trip_stops_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trip_stops_search", columns = { "location_id", "scheduled_departure_at", "trip_id" }),
        @JdbcIndex(
            name = "idx_trip_stops_province_search",
            columns = { "province_id", "scheduled_departure_at", "trip_id" }
        ),
        @JdbcIndex(name = "idx_trip_stops_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trip_stops_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TripStop extends BaseEntity {

    // ID chuyến trong carrier_trips.
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // ID địa điểm trong location_places.
    @Column(name = "location_id", nullable = false)
    private long locationId;

    // ID tỉnh/thành trong location_provinces.
    @Column(name = "province_id", nullable = false)
    private long provinceId;

    // Số thứ tự điểm dừng trên hành trình.
    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    // Snapshot tên địa điểm tiếng Việt.
    @Column(name = "name_vi", nullable = false, length = 120)
    private String nameVi;

    // Snapshot tên địa điểm tiếng Anh.
    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    // Snapshot địa chỉ điểm dừng.
    @Column(name = "address", nullable = false, length = 300)
    private String address;

    // Snapshot vĩ độ địa điểm tĩnh.
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    // Snapshot kinh độ địa điểm tĩnh.
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    // Giờ đến dự kiến tại điểm dừng, lưu UTC.
    @Column(name = "scheduled_arrival_at", nullable = false)
    private Instant scheduledArrivalAt;

    // Giờ rời dự kiến tại điểm dừng, lưu UTC.
    @Column(name = "scheduled_departure_at", nullable = false)
    private Instant scheduledDepartureAt;

    // Giờ tài xế xác nhận đã đến; null khi chưa đến.
    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    // Giờ tài xế xác nhận đã rời; null khi chưa rời.
    @Column(name = "actual_departure_at")
    private Instant actualDepartureAt;

    // Giá lũy kế từ đầu tuyến; giá chặng là hiệu giá hai điểm.
    @Column(name = "cumulative_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal cumulativeFare;

    // Cho phép đón khách tại điểm này.
    @Column(name = "pickup_allowed", nullable = false)
    private boolean pickupAllowed;

    // Cho phép trả khách tại điểm này.
    @Column(name = "dropoff_allowed", nullable = false)
    private boolean dropoffAllowed;
}
