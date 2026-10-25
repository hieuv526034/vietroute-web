package vn.vietroute.business.trip.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Điểm snapshot thuộc trip, không bị cascade sửa theo mẫu tuyến. */
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
public final class TripStop {

    private TripStop() {}
}
