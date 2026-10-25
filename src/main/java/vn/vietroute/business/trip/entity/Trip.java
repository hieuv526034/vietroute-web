package vn.vietroute.business.trip.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng chuyến; projection JDBC tường minh tách khỏi snapshot lưu DB. */
@JdbcTable(
    name = "carrier_trips",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_trips_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trips_carrier_status", columns = { "carrier_id", "status", "departure_at", "id" }),
        @JdbcIndex(name = "idx_trips_vehicle_schedule", columns = { "vehicle_id", "departure_at", "end_at" }),
        @JdbcIndex(name = "idx_trips_driver_schedule", columns = { "driver_id", "departure_at", "end_at" }),
        @JdbcIndex(name = "idx_trips_driver_user", columns = { "driver_user_id", "status", "departure_at" }),
        @JdbcIndex(name = "idx_trips_route", columns = { "route_id" }),
        @JdbcIndex(name = "idx_trips_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trips_updated_by", columns = { "updated_by" }),
    }
)
public final class Trip {

    private Trip() {}
}
