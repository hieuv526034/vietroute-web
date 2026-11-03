package vn.vietroute.business.trip.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.response.DriverPassengerResponse;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.trip.constant.TripProgressAction;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.business.trip.dto.search.TripSearch;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.business.trip.repo.TripRepo.Assets;
import vn.vietroute.business.trip.repo.TripStopRepo;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** DRIVER chỉ thao tác chuyến được giao; tiến độ có thứ tự, thời gian server và lịch sử nguyên tử. */
@Service
@RequiredArgsConstructor
public class DriverTripService {

    private final TripRepo trips;
    private final CarrierRepo carriers;
    private final TripStopRepo progress;
    private final BookingRepo bookings;

    /** Driver không cần grant GET quản trị booking; chỉ projection đón/trả sau kiểm chính phân công hiện hành. */
    @Transactional(readOnly = true)
    public PageResponse<DriverPassengerResponse> passengers(long id, PageRequest page, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        trips.assigned(id, false, actor.id());
        return bookings.passengers(id, page);
    }

    /** SQL count/page cùng ràng buộc người được phân công, không có bypass ADMIN. */
    public PageResponse<TripResponse> page(TripSearch search, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return trips.assignedPage(search, actor.id());
    }

    /** ID chuyến người khác trả 404, kể cả cùng nhà xe. */
    @Transactional(readOnly = true)
    public TripResponse find(long id, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return trips.assigned(id, false, actor.id());
    }

    /** Khóa trip trước, kiểm trạng thái/điểm, ghi actual/state/event cùng transaction. */
    @Transactional
    public TripResponse update(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getAction() != null, ErrorCode.INVALID_REQUEST);
        TripResponse trip = trips.assigned(id, true, actor.id());
        BusinessRules.require(
            body.getVersion() != null && body.getVersion() == trip.getVersion(),
            ErrorCode.STALE_VERSION
        );
        List<TripStopResponse> stops = trip.getStops();
        int count = stops.size();
        Instant now = Instant.now();
        BusinessRules.require(
            count >= 2 && body.getStopOrder() >= 1 && body.getStopOrder() <= count,
            ErrorCode.TRIP_STATE_CONFLICT
        );
        TripStopResponse stop = stops.get(body.getStopOrder() - 1);
        TripStatus state = trip.getStatus();
        String event;
        switch (body.getAction()) {
            case START -> {
                BusinessRules.require(
                    state == TripStatus.OPEN &&
                        body.getStopOrder() == 1 &&
                        stop.getActualArrivalAt() == null &&
                        !now.isBefore(trip.getDepartureAt().minusSeconds(1800)),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                Assets assets = trips.assets(
                    trip.getCarrierId(),
                    trip.getScopeId(),
                    trip.getVehicleId(),
                    trip.getDriverId()
                );
                carriers.requireDriverUser(assets.getDriverUserId(), trip.getScopeId());
                BusinessRules.require(
                    assets.getDriverUserId() == actor.id() && assets.getCapacity() >= trip.getCapacity(),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                trips.requireAvailable(id, assets, trip.getDepartureAt(), trip.getEndAt(), true);
                progress.mark(trip, 1, true, now, actor.id());
                progress.mark(trip, 1, false, now, actor.id());
                state = TripStatus.IN_PROGRESS;
                event = "STARTED";
            }
            case ARRIVE -> {
                BusinessRules.require(
                    state == TripStatus.IN_PROGRESS &&
                        body.getStopOrder() > 1 &&
                        stop.getActualArrivalAt() == null &&
                        stops.get(body.getStopOrder() - 2).getActualDepartureAt() != null &&
                        !now.isBefore(stops.get(body.getStopOrder() - 2).getActualDepartureAt()),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                progress.mark(trip, body.getStopOrder(), true, now, actor.id());
                event = "ARRIVED";
            }
            case DEPART -> {
                BusinessRules.require(
                    state == TripStatus.IN_PROGRESS &&
                        body.getStopOrder() > 1 &&
                        body.getStopOrder() < count &&
                        stop.getActualArrivalAt() != null &&
                        stop.getActualDepartureAt() == null &&
                        !now.isBefore(stop.getActualArrivalAt()),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                progress.mark(trip, body.getStopOrder(), false, now, actor.id());
                event = "DEPARTED";
            }
            case COMPLETE -> {
                BusinessRules.require(
                    state == TripStatus.IN_PROGRESS &&
                        body.getStopOrder() == count &&
                        stop.getActualArrivalAt() != null &&
                        stop.getActualDepartureAt() == null &&
                        !now.isBefore(stop.getActualArrivalAt()),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                progress.mark(trip, count, false, now, actor.id());
                state = TripStatus.COMPLETED;
                event = "COMPLETED";
            }
            default -> throw new IllegalArgumentException("Hành động tiến độ chưa hỗ trợ");
        }
        if (
            body.getAction() == TripProgressAction.START || body.getAction() == TripProgressAction.DEPART
        ) bookings.departed(id, body.getStopOrder(), actor.id());
        if (body.getAction() == TripProgressAction.ARRIVE) bookings.arrived(id, body.getStopOrder(), actor.id());
        trips.state(trip, state, trip.getOperationNote(), actor.id());
        trips.event(
            id,
            trip.getCarrierId(),
            trip.getScopeId(),
            event,
            body.getStopOrder(),
            body.getNote().strip(),
            actor.id()
        );
        return trips.assigned(id, false, actor.id());
    }
}
