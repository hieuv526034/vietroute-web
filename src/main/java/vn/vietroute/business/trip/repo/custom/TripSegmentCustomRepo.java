package vn.vietroute.business.trip.repo.custom;

import java.sql.Array;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.BusinessRules;

/** SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ. */
@Repository
public class TripSegmentCustomRepo {

    /** Khởi tạo toàn bộ chặng bằng một batch, caller đã tạo và khóa chuyến. */
    public void insertBatch(
        Connection connection,
        long id,
        long carrierId,
        long scopeId,
        int stopCount,
        int capacity,
        long actor
    ) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO trip_segments(trip_id, carrier_id, scope_id, segment_order, capacity, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            for (int order = 1; order < stopCount; order++) {
                query.setLong(1, id);
                query.setLong(2, carrierId);
                query.setLong(3, scopeId);
                query.setInt(4, order);
                query.setInt(5, capacity);
                query.setLong(6, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    /** Cập nhật sức chứa theo tập sau khi caller kiểm mức giữ chỗ và khóa đủ chặng. */
    public void updateCapacity(Connection connection, long id, int capacity, long actor) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE trip_segments
                SET capacity = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE trip_id = ?
                """
            )
        ) {
            query.setInt(1, capacity);
            query.setLong(2, actor);
            query.setLong(3, id);
            query.executeUpdate();
        }
    }

    public void change(Connection connection, long tripId, int pickup, int dropoff, int delta, long actor, int expected)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE trip_segments
                SET reserved_seats = reserved_seats + ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE trip_id = ? AND segment_order >= ? AND segment_order < ?
                """
            )
        ) {
            query.setInt(1, delta);
            query.setLong(2, actor);
            query.setLong(3, tripId);
            query.setInt(4, pickup);
            query.setInt(5, dropoff);
            BusinessRules.require(query.executeUpdate() == expected, ErrorCode.BOOKING_INVALID);
        }
    }

    public void releaseTrip(
        Connection connection,
        long tripId,
        long actor,
        List<Integer> orders,
        List<Long> seats,
        List<Long> changes
    ) throws SQLException {
        Array orderArray = connection.createArrayOf("integer", orders.toArray(Integer[]::new));
        Array seatArray = connection.createArrayOf("bigint", seats.toArray(Long[]::new));
        Array changeArray = connection.createArrayOf("bigint", changes.toArray(Long[]::new));
        try {
            // Version vẫn tăng bằng số đơn chạm đoạn như vòng lặp cũ; chỉ giảm lượt JDBC, không đổi audit lịch sử.
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    UPDATE trip_segments s
                    SET reserved_seats = s.reserved_seats - needed.seats, version = s.version + needed.changes,
                        updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    FROM UNNEST(?::INTEGER [], ?::BIGINT [], ?::BIGINT []) needed(segment_order, seats, changes)
                    WHERE s.trip_id = ? AND s.segment_order = needed.segment_order
                    """
                )
            ) {
                query.setLong(1, actor);
                query.setArray(2, orderArray);
                query.setArray(3, seatArray);
                query.setArray(4, changeArray);
                query.setLong(5, tripId);
                BusinessRules.require(query.executeUpdate() == orders.size(), ErrorCode.BOOKING_INVALID);
            }
        } finally {
            orderArray.free();
            seatArray.free();
            changeArray.free();
        }
    }
}
