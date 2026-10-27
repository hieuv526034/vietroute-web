package vn.vietroute.business.booking.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Một thuật toán tồn chỗ dùng cho WEB/PHONE/xác nhận/hủy; caller bắt buộc khóa trip trước. */
@Repository
public class TripCapacityRepository {

    /** Khóa đủ đoạn tăng dần; không tăng khi PENDING. Lỗi đoạn bất kỳ rollback toàn bộ giao dịch caller. */
    public void change(
        Connection connection,
        long tripId,
        int pickup,
        int dropoff,
        int seats,
        boolean reserve,
        long actor
    ) throws SQLException {
        var orders = new ArrayList<Integer>();
        try (
            var query = connection.prepareStatement(
                "SELECT segment_order,capacity,reserved_seats FROM trip_segments WHERE trip_id=? AND segment_order>=? AND segment_order<? ORDER BY segment_order FOR UPDATE"
            )
        ) {
            query.setLong(1, tripId);
            query.setInt(2, pickup);
            query.setInt(3, dropoff);
            try (var row = query.executeQuery()) {
                while (row.next()) {
                    BusinessRules.require(row.getInt(1) == pickup + orders.size(), ErrorCode.BOOKING_INVALID);
                    BusinessRules.require(
                        reserve ? row.getInt(2) - row.getInt(3) >= seats : row.getInt(3) >= seats,
                        ErrorCode.BOOKING_CAPACITY
                    );
                    orders.add(row.getInt(1));
                }
            }
        }
        BusinessRules.require(
            seats > 0 && orders.size() == dropoff - pickup && !orders.isEmpty(),
            ErrorCode.BOOKING_INVALID
        );
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_segments SET reserved_seats=reserved_seats+?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=? AND segment_order>=? AND segment_order<?"
            )
        ) {
            query.setInt(1, reserve ? seats : -seats);
            query.setLong(2, actor);
            query.setLong(3, tripId);
            query.setInt(4, pickup);
            query.setInt(5, dropoff);
            BusinessRules.require(query.executeUpdate() == orders.size(), ErrorCode.BOOKING_INVALID);
        }
    }

    /** Hủy trip đã khóa booking: cộng chỗ cần trả theo đoạn trước, khóa đoạn tăng dần và cập nhật một lần. */
    public void releaseTrip(Connection connection, long tripId, long actor) throws SQLException {
        var orders = new ArrayList<Integer>();
        var seats = new ArrayList<Long>();
        var changes = new ArrayList<Long>();
        try (
            var query = connection.prepareStatement(
                """
                SELECT part.segment_order,sum(b.seats),count(*) FROM bookings b
                CROSS JOIN LATERAL generate_series(b.pickup_order,b.dropoff_order-1) part(segment_order)
                WHERE b.trip_id=? AND b.status='CONFIRMED'
                GROUP BY part.segment_order ORDER BY part.segment_order
                """
            )
        ) {
            query.setLong(1, tripId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    orders.add(rows.getInt(1));
                    seats.add(rows.getLong(2));
                    changes.add(rows.getLong(3));
                }
            }
        }
        if (orders.isEmpty()) return;
        var orderArray = connection.createArrayOf("integer", orders.toArray(Integer[]::new));
        var seatArray = connection.createArrayOf("bigint", seats.toArray(Long[]::new));
        var changeArray = connection.createArrayOf("bigint", changes.toArray(Long[]::new));
        try {
            try (
                var query = connection.prepareStatement(
                    """
                    SELECT segment_order,reserved_seats FROM trip_segments
                    WHERE trip_id=? AND segment_order=ANY(?) ORDER BY segment_order FOR UPDATE
                    """
                )
            ) {
                query.setLong(1, tripId);
                query.setArray(2, orderArray);
                int count = 0;
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        BusinessRules.require(rows.getInt(1) == orders.get(count), ErrorCode.BOOKING_INVALID);
                        BusinessRules.require(rows.getLong(2) >= seats.get(count), ErrorCode.BOOKING_CAPACITY);
                        count++;
                    }
                }
                BusinessRules.require(count == orders.size(), ErrorCode.BOOKING_INVALID);
            }
            // Version vẫn tăng bằng số đơn chạm đoạn như vòng lặp cũ; chỉ giảm lượt JDBC, không đổi audit lịch sử.
            try (
                var query = connection.prepareStatement(
                    """
                    UPDATE trip_segments s SET reserved_seats=s.reserved_seats-needed.seats,
                        version=s.version+needed.changes,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    FROM unnest(?::integer[],?::bigint[],?::bigint[]) needed(segment_order,seats,changes)
                    WHERE s.trip_id=? AND s.segment_order=needed.segment_order
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

    /** Caller đã khóa trip; kiểm mỗi đoạn và trả chỗ nhỏ nhất cho cặp điểm, không tính toàn chuyến. */
    public int available(Connection connection, long tripId, int pickup, int dropoff) throws SQLException {
        int result = 200,
            count = 0;
        try (
            var query = connection.prepareStatement(
                "SELECT segment_order,capacity-reserved_seats FROM trip_segments WHERE trip_id=? AND segment_order>=? AND segment_order<? ORDER BY segment_order"
            )
        ) {
            query.setLong(1, tripId);
            query.setInt(2, pickup);
            query.setInt(3, dropoff);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    BusinessRules.require(rows.getInt(1) == pickup + count, ErrorCode.BOOKING_INVALID);
                    result = Math.min(result, rows.getInt(2));
                    count++;
                }
            }
        }
        BusinessRules.require(count == dropoff - pickup && count > 0, ErrorCode.BOOKING_INVALID);
        return result;
    }
}
