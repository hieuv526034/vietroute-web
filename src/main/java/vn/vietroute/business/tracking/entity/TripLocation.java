package vn.vietroute.business.tracking.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Mẫu thiết bị báo được lưu trực tiếp PostgreSQL, không phải chứng nhận vị trí vật lý. */
@JdbcTable(
    name = "trip_locations",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "trip_locations_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trip_locations_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_trip_locations_received", columns = { "received_at", "id" }),
        @JdbcIndex(name = "idx_trip_locations_driver", columns = { "driver_user_id" }),
        @JdbcIndex(name = "idx_trip_locations_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trip_locations_updated_by", columns = { "updated_by" }),
    }
)
public final class TripLocation {

    private TripLocation() {}
}
