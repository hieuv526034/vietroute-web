package vn.vietroute.business.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;

/** Entity đầy đủ cột carrier_route_stops; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carrier_route_stops",
    indexes = {
        @Index(name = "carrier_route_stops_pkey", columnList = "id", unique = true),
        @Index(name = "idx_route_stops_location", columnList = "location_id, route_id"),
        @Index(name = "idx_route_stops_created_by", columnList = "created_by"),
        @Index(name = "idx_route_stops_updated_by", columnList = "updated_by"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RouteStop extends BaseEntity {

    // Nhóm danh mục cố định do DB sinh, bảo vệ FK không trỏ nhầm loại.
    @Column(name = "category_location_ref", insertable = false, updatable = false, length = 64)
    private String categoryLocationRef;

    // ID tuyến mẫu trong carrier_routes.
    @Column(name = "route_id", nullable = false)
    private long routeId;

    // ID nhà xe sở hữu dữ liệu.
    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // ID địa điểm trong location_places.
    @Column(name = "location_id", nullable = false)
    private long locationId;

    // Số thứ tự điểm dừng trên hành trình.
    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    // Số phút đến điểm này tính từ giờ khởi hành tuyến.
    @Column(name = "arrival_offset_minutes", nullable = false)
    private int arrivalOffsetMinutes;

    // Số phút rời điểm này tính từ giờ khởi hành tuyến.
    @Column(name = "departure_offset_minutes", nullable = false)
    private int departureOffsetMinutes;

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
