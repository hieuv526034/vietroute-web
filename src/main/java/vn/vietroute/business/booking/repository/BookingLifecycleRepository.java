package vn.vietroute.business.booking.repository;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.booking.entity.BookingStatus;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Hooks sau guard/khóa trip; mọi cập nhật và history dùng cùng transaction, không mở connection hoặc commit riêng. */
@Repository
@RequiredArgsConstructor
public class BookingLifecycleRepository {

    private final BookingRepository bookings;
    private final TripCapacityRepository capacity;

    /** Trip → booking tăng ID → segments tăng dần; cộng chỗ theo đoạn, không query/save theo từng đơn. */
    public void cancelTrip(Connection connection, long tripId, String reason, long actor) throws SQLException {
        bookings.lockActiveForTrip(connection, tripId);
        capacity.releaseTrip(connection, tripId, actor);
        transition(connection, tripId, 0, BookingStatus.CANCELLED, reason, actor);
    }

    /** Chỉ đóng PENDING đã lỡ điểm đón; tập còn lại và số chỗ không thay đổi. */
    public void departed(Connection connection, long tripId, int order, long actor) throws SQLException {
        transition(
            connection,
            tripId,
            order,
            BookingStatus.REJECTED,
            "Điểm đón đã được tài xế xác nhận rời / Pickup stop departure confirmed",
            actor
        );
    }

    /** Đến điểm trả chỉ hoàn thành CONFIRMED; giữ nguyên số chỗ lịch sử. */
    public void arrived(Connection connection, long tripId, int order, long actor) throws SQLException {
        transition(
            connection,
            tripId,
            order,
            BookingStatus.COMPLETED,
            "Đã đến điểm trả theo xác nhận tài xế / Driver confirmed arrival at drop-off",
            actor
        );
    }

    /** SELECT khóa theo ID, CAS và ghi đủ history trong một statement; mismatch/history lỗi làm caller rollback cả trip/chỗ. */
    private void transition(Connection connection, long tripId, int order, BookingStatus state, String note, long actor)
        throws SQLException {
        String predicate = switch (state) {
            case CANCELLED -> "b.status IN('PENDING','CONFIRMED')";
            case REJECTED -> "b.status='PENDING' AND b.pickup_order<=?";
            case COMPLETED -> "b.status='CONFIRMED' AND b.dropoff_order<=?";
            default -> throw new IllegalArgumentException("Lifecycle không hỗ trợ trạng thái này");
        };
        String sql =
            """
            WITH parameters AS (SELECT ?::text AS state,?::text AS note,?::bigint AS actor),
            targets AS MATERIALIZED (
                SELECT b.id,b.trip_id,b.carrier_id,b.scope_id,b.status,b.version FROM bookings b
                WHERE b.trip_id=? AND
            """ +
            predicate +
            """
                ORDER BY b.id FOR UPDATE OF b
            ), changed AS (
                UPDATE bookings b SET status=p.state,updated_at=CURRENT_TIMESTAMP,updated_by=p.actor,version=b.version+1
                FROM targets old,parameters p
                WHERE b.id=old.id AND b.trip_id=old.trip_id AND b.carrier_id=old.carrier_id
                    AND b.scope_id=old.scope_id AND b.version=old.version
                RETURNING b.id,b.trip_id,b.carrier_id,b.scope_id,old.status AS from_status
            ), logged AS (
                INSERT INTO booking_events(booking_id,trip_id,carrier_id,scope_id,from_status,to_status,event_type,note,amount,created_by)
                SELECT c.id,c.trip_id,c.carrier_id,c.scope_id,c.from_status,p.state,p.state,p.note,0,p.actor
                FROM changed c CROSS JOIN parameters p ORDER BY c.id RETURNING booking_id
            )
            SELECT (SELECT count(*) FROM targets),(SELECT count(*) FROM changed),(SELECT count(*) FROM logged)
            """;
        try (var query = connection.prepareStatement(sql)) {
            query.setString(1, state.name());
            query.setString(2, note);
            query.setLong(3, actor);
            query.setLong(4, tripId);
            if (state != BookingStatus.CANCELLED) query.setInt(5, order);
            try (var row = query.executeQuery()) {
                row.next();
                BusinessRules.require(
                    row.getLong(1) == row.getLong(2) && row.getLong(1) == row.getLong(3),
                    ErrorCode.STALE_VERSION
                );
            }
        }
    }
}
