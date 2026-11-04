package vn.vietroute.business.trip.service;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.booking.repo.BookingRepo.Stop;
import vn.vietroute.business.booking.repo.BookingRepo.Trip;
import vn.vietroute.business.booking.service.BookingService;
import vn.vietroute.business.trip.dto.response.JourneyMatchResponse;
import vn.vietroute.business.trip.dto.response.JourneyQuoteResponse;
import vn.vietroute.business.trip.dto.response.JourneyResponse;
import vn.vietroute.business.trip.dto.search.JourneyQuoteSearch;
import vn.vietroute.business.trip.dto.search.JourneySearch;
import vn.vietroute.business.trip.repo.JourneyRepo;
import vn.vietroute.business.trip.repo.TripSegmentRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Public chỉ dùng projection đã whitelist; không gọi staff service hoặc bỏ guard qua DB public flags. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyRepo journeys;
    private final BookingRepo bookings;
    private final TripSegmentRepo capacity;

    /** Báo giá/chỗ server cùng chính sách khi tạo/xác nhận; đây không phải thao tác giữ chỗ. */
    public JourneyQuoteResponse quote(long id, JourneyQuoteSearch body) {
        journeys.find(id);
        Trip trip = bookings.trip(id, false);
        Stop pickup = bookings.stop(id, body.getPickupStopId());
        Stop dropoff = bookings.stop(id, body.getDropoffStopId());
        BusinessRules.require(
            body.getSeats() > 0 &&
                body.getSeats() <= 200 &&
                pickup.isPickup() &&
                dropoff.isDropoff() &&
                dropoff.getOrder() > pickup.getOrder() &&
                dropoff.getFare().compareTo(pickup.getFare()) >= 0,
            ErrorCode.BOOKING_INVALID
        );
        int available = capacity.available(id, pickup.getOrder(), dropoff.getOrder());
        BigDecimal unit = dropoff.getFare().subtract(pickup.getFare());
        return new JourneyQuoteResponse(
            unit,
            unit.multiply(BigDecimal.valueOf(body.getSeats())),
            available,
            BookingService.accepting(trip, pickup, Instant.now()) && available >= body.getSeats()
        );
    }

    /** Count/page đúng pickup local date và remaining theo chặng, không tính lại tại JS. */
    public PageResponse<JourneyMatchResponse> search(JourneySearch body) {
        BusinessRules.require(
            body.getDate() != null &&
                body.isRouteDefined() &&
                body.isPriceRangeValid() &&
                body.getSeats() > 0 &&
                body.getSeats() <= 200,
            ErrorCode.INVALID_REQUEST
        );
        return journeys.search(body);
    }

    /** Chỉ công bố trip đã từng mở, không chứa dữ liệu hành khách hoặc tài xế. */
    public JourneyResponse find(long id) {
        return journeys.find(id);
    }
}
