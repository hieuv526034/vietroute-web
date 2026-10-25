package vn.vietroute.business.route.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata điểm mẫu; offset phút khác timestamp chuyến được tạo từ mẫu. */
@Getter
@Setter
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
public class RouteStop extends BaseEntity {

    private long routeId;
    private long carrierId;
    private long scopeId;
    private long locationId;
    private int stopOrder;
    private int arrivalOffsetMinutes;
    private int departureOffsetMinutes;
    private BigDecimal cumulativeFare;
    private boolean pickupAllowed;
    private boolean dropoffAllowed;
}
