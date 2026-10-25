package vn.vietroute.business.tracking.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata trạng thái consent hiện hành; không xuất session key qua DTO xem vị trí. */
@JdbcTable(
    name = "trip_tracking",
    primaryKey = { "trip_id" },
    indexes = {
        @JdbcIndex(name = "trip_tracking_pkey", columns = { "trip_id" }, unique = true),
        @JdbcIndex(name = "idx_trip_tracking_driver", columns = { "driver_user_id" }),
        @JdbcIndex(name = "idx_trip_tracking_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trip_tracking_updated_by", columns = { "updated_by" }),
    }
)
public final class TripTracking {

    private TripTracking() {}
}
