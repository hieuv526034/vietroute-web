package vn.vietroute.business.tracking.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** GPS không công khai: chỉ owner của đơn CONFIRMED còn hiệu lực, kiểm lại trong SQL lấy snapshot. */
@Service
@RequiredArgsConstructor
public class CustomerTrackingService {

    private final BookingRepository bookings;
    private final TrackingRepository tracking;
    private final JdbcTransactions transactions;
    private final TrackingSettings settings;
    private final Clock clock;

    public TrackingResponse read(long bookingId, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), false);
            BookingResponse booking = bookings.find(connection, bookingId, actor.id(), false, false).response();
            return tracking.view(
                connection,
                booking.tripId(),
                false,
                bookingId,
                actor.id(),
                settings.isEnabled(),
                clock.instant()
            );
        });
    }
}
