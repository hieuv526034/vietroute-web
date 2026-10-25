package vn.vietroute.business.booking.service;

import java.time.Instant;
import vn.vietroute.business.booking.repository.BookingRepository.Stop;
import vn.vietroute.business.booking.repository.BookingRepository.Trip;
import vn.vietroute.business.trip.entity.TripStatus;

/** Chính sách nhận khách dùng chung quote/create/confirm; không suy qua điểm từ đồng hồ khi đang chạy trễ. */
public final class BookingAvailability {

    private BookingAvailability() {}

    public static boolean accepting(Trip trip, Stop pickup, Instant now) {
        return (
            trip.accepting() &&
            pickup.actualDeparture() == null &&
            (trip.status() == TripStatus.IN_PROGRESS ||
                (trip.status() == TripStatus.OPEN && trip.departureAt().isAfter(now)))
        );
    }
}
