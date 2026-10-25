package vn.vietroute.business.booking.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** History cùng giao dịch, không có API sửa/xóa sự kiện. */
@JdbcTable(
    name = "booking_events",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "booking_events_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_booking_events_parent", columns = { "booking_id", "id" }),
        @JdbcIndex(name = "idx_booking_events_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_booking_events_created_by", columns = { "created_by" }),
    }
)
public final class BookingEvent {

    private BookingEvent() {}
}
