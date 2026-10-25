package vn.vietroute.business.trip.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Lịch sử vận hành ghi trong transaction của chuyến, không sửa sự kiện cũ. */
@JdbcTable(
    name = "trip_events",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "trip_events_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trip_events_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_trip_events_created_by", columns = { "created_by" }),
    }
)
public final class TripEvent {

    private TripEvent() {}
}
