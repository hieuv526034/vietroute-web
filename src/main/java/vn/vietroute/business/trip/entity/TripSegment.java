package vn.vietroute.business.trip.entity;

import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Tồn chỗ theo từng đoạn liên tiếp; mutation khóa trip trước rồi segment tăng dần. */
@JdbcTable(
    name = "trip_segments",
    primaryKey = { "trip_id", "segment_order" },
    indexes = {
        @JdbcIndex(name = "trip_segments_pkey", columns = { "trip_id", "segment_order" }, unique = true),
        @JdbcIndex(name = "idx_trip_segments_updated_by", columns = { "updated_by" }),
    }
)
public final class TripSegment {

    private TripSegment() {}
}
