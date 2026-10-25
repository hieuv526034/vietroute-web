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
                "UPDATE trip_segments SET reserved_seats=reserved_seats+?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=? AND segment_order=?"
            )
        ) {
            for (int order : orders) {
                query.setInt(1, reserve ? seats : -seats);
                query.setLong(2, actor);
                query.setLong(3, tripId);
                query.setInt(4, order);
                query.addBatch();
            }
            query.executeBatch();
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
