package vn.vietroute.business.journey.service;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.business.booking.repository.TripCapacityRepository;
import vn.vietroute.business.booking.service.BookingAvailability;
import vn.vietroute.business.journey.dto.request.JourneyQuoteRequest;
import vn.vietroute.business.journey.dto.request.JourneySearchRequest;
import vn.vietroute.business.journey.dto.response.JourneyMatchResponse;
import vn.vietroute.business.journey.dto.response.JourneyQuoteResponse;
import vn.vietroute.business.journey.dto.response.JourneyResponse;
import vn.vietroute.business.journey.repository.JourneyRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Public chỉ dùng projection đã whitelist; không gọi staff service hoặc bỏ guard qua DB public flags. */
@Service
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyRepository journeys;
    private final JdbcTransactions transactions;
    private final BookingRepository bookings;
    private final TripCapacityRepository capacity;

    /** Báo giá/chỗ server cùng chính sách khi tạo/xác nhận; đây không phải thao tác giữ chỗ. */
    public JourneyQuoteResponse quote(long id, JourneyQuoteRequest body) {
        return transactions.execute(connection -> {
            journeys.find(connection, id);
            var trip = bookings.trip(connection, id, false);
            var pickup = bookings.stop(connection, id, body.getPickupStopId());
            var dropoff = bookings.stop(connection, id, body.getDropoffStopId());
            BusinessRules.require(
                body.getSeats() > 0 &&
                    body.getSeats() <= 200 &&
                    pickup.pickup() &&
                    dropoff.dropoff() &&
                    dropoff.order() > pickup.order() &&
                    dropoff.fare().compareTo(pickup.fare()) >= 0,
                ErrorCode.BOOKING_INVALID
            );
            int available = capacity.available(connection, id, pickup.order(), dropoff.order());
            var unit = dropoff.fare().subtract(pickup.fare());
            return new JourneyQuoteResponse(
                unit,
                unit.multiply(BigDecimal.valueOf(body.getSeats())),
                available,
                BookingAvailability.accepting(trip, pickup, Instant.now()) && available >= body.getSeats()
            );
        });
    }

    /** Count/page đúng pickup local date và remaining theo chặng, không tính lại tại JS. */
    public PageResponse<JourneyMatchResponse> search(JourneySearchRequest body) {
        BusinessRules.require(
            body.getDate() != null && body.isRouteDefined() && body.getSeats() > 0 && body.getSeats() <= 200,
            ErrorCode.INVALID_REQUEST
        );
        return journeys.search(body);
    }

    /** Chỉ công bố trip đã từng mở, không chứa dữ liệu hành khách hoặc tài xế. */
    public JourneyResponse find(long id) {
        return transactions.execute(connection -> journeys.find(connection, id));
    }
}
