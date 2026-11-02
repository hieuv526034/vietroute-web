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
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

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
@JdbcTable(
    name = "carrier_route_stops",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_route_stops_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_route_stops_location", columns = { "location_id", "route_id" }),
        @JdbcIndex(name = "idx_route_stops_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_route_stops_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RouteStop extends BaseEntity {

    @Column(name = "route_id", nullable = false)
    private long routeId;

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "location_id", nullable = false)
    private long locationId;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Column(name = "arrival_offset_minutes", nullable = false)
    private int arrivalOffsetMinutes;

    @Column(name = "departure_offset_minutes", nullable = false)
    private int departureOffsetMinutes;

    @Column(name = "cumulative_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal cumulativeFare;

    @Column(name = "pickup_allowed", nullable = false)
    private boolean pickupAllowed;

    @Column(name = "dropoff_allowed", nullable = false)
    private boolean dropoffAllowed;
}
