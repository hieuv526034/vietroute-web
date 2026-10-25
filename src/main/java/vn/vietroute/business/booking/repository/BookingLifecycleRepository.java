package vn.vietroute.business.booking.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.entity.BookingStatus;

/** Hooks chỉ gọi sau guard/khóa trip của service chuyến/tài xế; không mở connection hoặc commit riêng. */
@Repository
@RequiredArgsConstructor
public class BookingLifecycleRepository {

    private final BookingRepository bookings;
    private final TripCapacityRepository capacity;

    /** Hủy trước khởi hành: trip → toàn bộ booking tăng ID → segments; release CONFIRMED đúng một lần. */
    public void cancelTrip(Connection connection, long tripId, String reason, long actor) throws SQLException {
        for (var row : bookings.activeForTrip(connection, tripId)) {
            if (row.status() == BookingStatus.CONFIRMED) capacity.change(
                connection,
                tripId,
                row.pickupOrder(),
                row.dropoffOrder(),
                row.seats(),
                false,
                actor
            );
            transition(connection, row, BookingStatus.CANCELLED, reason, actor);
        }
    }

    /** Rời điểm đóng PENDING đã lỡ điểm đón; không tự xác nhận hoặc lấy GPS để đổi đơn. */
    public void departed(Connection connection, long tripId, int order, long actor) throws SQLException {
        for (var row : bookings.activeForTrip(connection, tripId))
            if (row.status() == BookingStatus.PENDING && row.pickupOrder() <= order) transition(
                connection,
                row,
                BookingStatus.REJECTED,
                "Điểm đón đã được tài xế xác nhận rời / Pickup stop departure confirmed",
                actor
            );
    }

    /** Đến điểm trả hoàn thành đơn CONFIRMED, giữ số chỗ lịch sử thay vì tái dùng cho trip khác. */
    public void arrived(Connection connection, long tripId, int order, long actor) throws SQLException {
        for (var row : bookings.activeForTrip(connection, tripId))
            if (row.status() == BookingStatus.CONFIRMED && row.dropoffOrder() <= order) transition(
                connection,
                row,
                BookingStatus.COMPLETED,
                "Đã đến điểm trả theo xác nhận tài xế / Driver confirmed arrival at drop-off",
                actor
            );
    }

    private void transition(Connection connection, BookingResponse row, BookingStatus state, String note, long actor)
        throws SQLException {
        bookings.state(connection, row, state, actor);
        bookings.event(connection, row, state, state.name(), note, BigDecimal.ZERO, null, actor);
    }
}
