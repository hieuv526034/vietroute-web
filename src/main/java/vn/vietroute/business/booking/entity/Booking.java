package vn.vietroute.business.booking.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata kiểm schema/index, không là ORM hoặc DTO trả credentials. */
@JdbcTable(
    name = "bookings",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "bookings_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_bookings_one_active_web", columns = { "customer_id" }, unique = true),
        @JdbcIndex(name = "idx_bookings_trip_status", columns = { "trip_id", "status", "id" }),
        @JdbcIndex(name = "idx_bookings_carrier_status", columns = { "carrier_id", "status", "created_at", "id" }),
        @JdbcIndex(name = "idx_bookings_customer", columns = { "customer_id", "created_at", "id" }),
        @JdbcIndex(name = "idx_bookings_pickup", columns = { "pickup_stop_id", "status" }),
        @JdbcIndex(name = "idx_bookings_dropoff", columns = { "dropoff_stop_id", "status" }),
        @JdbcIndex(name = "idx_bookings_source", columns = { "source_id" }),
        @JdbcIndex(name = "idx_bookings_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_bookings_updated_by", columns = { "updated_by" }),
    }
)
public final class Booking {

    private Booking() {}
}
