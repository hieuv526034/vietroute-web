package vn.vietroute.business.trip.repo;

import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Một thuật toán tồn chỗ dùng cho WEB/PHONE/xác nhận/hủy; caller bắt buộc khóa trip trước. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class TripSegmentRepo {

    private final EntityManager entityManager;
    private final TripSegmentCustomRepo batch;

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
        ArrayList<Integer> orders = new ArrayList<Integer>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT segment_order, capacity, reserved_seats
                FROM {h-schema}trip_segments
                WHERE trip_id = :tripId AND segment_order >= :pickup AND segment_order < :dropoff
                ORDER BY segment_order
                FOR UPDATE
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            query.setParameter("pickup", pickup);
            query.setParameter("dropoff", dropoff);
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;

                    BusinessRules.require(intValue(row.get(0)) == pickup + orders.size(), ErrorCode.BOOKING_INVALID);
                    BusinessRules.require(
                        reserve ? intValue(row.get(1)) - intValue(row.get(2)) >= seats : intValue(row.get(2)) >= seats,
                        ErrorCode.BOOKING_CAPACITY
                    );
                    orders.add(intValue(row.get(0)));
                }
            }
        }
        BusinessRules.require(
            seats > 0 && orders.size() == dropoff - pickup && !orders.isEmpty(),
            ErrorCode.BOOKING_INVALID
        );
        batch.change(connection, tripId, pickup, dropoff, reserve ? seats : -seats, actor, orders.size());
    }

    /** Hủy trip đã khóa booking: cộng chỗ cần trả theo đoạn trước, khóa đoạn tăng dần và cập nhật một lần. */
    public void releaseTrip(Connection connection, long tripId, long actor) throws SQLException {
        ArrayList<Integer> orders = new ArrayList<Integer>();
        ArrayList<Long> seats = new ArrayList<Long>();
        ArrayList<Long> changes = new ArrayList<Long>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT part.segment_order, SUM(b.seats), COUNT(*)
                FROM {h-schema}bookings b
                CROSS JOIN LATERAL GENERATE_SERIES(b.pickup_order, b.dropoff_order - 1) part(segment_order)
                WHERE b.trip_id = :tripId
                    AND b.status = 'CONFIRMED'
                GROUP BY part.segment_order
                ORDER BY part.segment_order
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    orders.add(intValue(rows.get(0)));
                    seats.add(longValue(rows.get(1)));
                    changes.add(longValue(rows.get(2)));
                }
            }
        }
        if (orders.isEmpty()) return;
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT segment_order, reserved_seats
                FROM {h-schema}trip_segments
                WHERE trip_id = :tripId
                    AND segment_order = ANY(:orderArray)
                ORDER BY segment_order
                FOR UPDATE
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            query.setParameter("orderArray", orders.toArray(Integer[]::new));
            int count = 0;
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    BusinessRules.require(intValue(rows.get(0)) == orders.get(count), ErrorCode.BOOKING_INVALID);
                    BusinessRules.require(longValue(rows.get(1)) >= seats.get(count), ErrorCode.BOOKING_CAPACITY);
                    count++;
                }
            }
            BusinessRules.require(count == orders.size(), ErrorCode.BOOKING_INVALID);
        }
        batch.releaseTrip(connection, tripId, actor, orders, seats, changes);
    }

    /** Caller đã khóa trip; kiểm mỗi đoạn và trả chỗ nhỏ nhất cho cặp điểm, không tính toàn chuyến. */
    public int available(long tripId, int pickup, int dropoff) {
        int result = 200,
            count = 0;
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT segment_order, capacity - reserved_seats
                FROM {h-schema}trip_segments
                WHERE trip_id = :tripId AND segment_order >= :pickup AND segment_order < :dropoff
                ORDER BY segment_order
                """,
                Tuple.class
            );

            query.setParameter("tripId", tripId);
            query.setParameter("pickup", pickup);
            query.setParameter("dropoff", dropoff);
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;

                    BusinessRules.require(intValue(rows.get(0)) == pickup + count, ErrorCode.BOOKING_INVALID);
                    result = Math.min(result, intValue(rows.get(1)));
                    count++;
                }
            }
        }
        BusinessRules.require(count == dropoff - pickup && count > 0, ErrorCode.BOOKING_INVALID);
        return result;
    }

    /** Trip đã khóa; khóa segment theo thứ tự tăng và lấy mức đặt cao nhất trước khi đổi capacity. */
    public int peakReserved(Connection connection, long id) throws SQLException {
        int peak = 0;
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT reserved_seats
                FROM {h-schema}trip_segments
                WHERE trip_id = :id
                ORDER BY segment_order
                FOR UPDATE
                """,
                Tuple.class
            );

            query.setParameter("id", id);
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    peak = Math.max(peak, intValue(row.get(0)));
                }
            }
        }
        return peak;
    }
}
