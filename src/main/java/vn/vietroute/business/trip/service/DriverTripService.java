package vn.vietroute.business.trip.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** DRIVER chỉ thao tác chuyến được giao; tiến độ có thứ tự, thời gian server và lịch sử nguyên tử. */
@Service
@RequiredArgsConstructor
public class DriverTripService {

    private final TripRepo trips;
    private final CarrierRepo carriers;
    private final TripStopRepo progress;
    private final JdbcTransactions transactions;
    private final BookingRepo bookings;

    /** Driver không cần grant GET quản trị booking; chỉ projection đón/trả sau kiểm chính phân công hiện hành. */
    public PageResponse<DriverPassengerResponse> passengers(long id, PageRequest page, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, false, actor.id());
            return bookings.passengers(connection, id, page);
        });
    }

    /** SQL count/page cùng ràng buộc người được phân công, không có bypass ADMIN. */
    public PageResponse<TripResponse> page(TripSearch search, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return trips.assignedPage(search, actor.id());
    }

    /** ID chuyến người khác trả 404, kể cả cùng nhà xe. */
    public TripResponse find(long id, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> trips.assigned(connection, id, false, actor.id()));
    }

    /** Khóa trip trước, kiểm trạng thái/điểm, ghi actual/state/event cùng transaction. */
    public TripResponse update(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getAction() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            TripResponse trip = trips.assigned(connection, id, true, actor.id());
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
                        connection,
                        trip.getCarrierId(),
                        trip.getScopeId(),
                        trip.getVehicleId(),
                        trip.getDriverId()
                    );
                    carriers.requireDriverUser(connection, assets.getDriverUserId(), trip.getScopeId());
                    BusinessRules.require(
                        assets.getDriverUserId() == actor.id() && assets.getCapacity() >= trip.getCapacity(),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    trips.requireAvailable(connection, id, assets, trip.getDepartureAt(), trip.getEndAt(), true);
                    progress.mark(connection, trip, 1, true, now, actor.id());
                    progress.mark(connection, trip, 1, false, now, actor.id());
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
                    progress.mark(connection, trip, body.getStopOrder(), true, now, actor.id());
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
                    progress.mark(connection, trip, body.getStopOrder(), false, now, actor.id());
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
                    progress.mark(connection, trip, count, false, now, actor.id());
                    state = TripStatus.COMPLETED;
                    event = "COMPLETED";
                }
                default -> throw new IllegalArgumentException("Hành động tiến độ chưa hỗ trợ");
            }
            if (
                body.getAction() == TripProgressAction.START || body.getAction() == TripProgressAction.DEPART
            ) bookings.departed(connection, id, body.getStopOrder(), actor.id());
            if (body.getAction() == TripProgressAction.ARRIVE) bookings.arrived(
                connection,
                id,
                body.getStopOrder(),
                actor.id()
            );
            trips.state(connection, trip, state, trip.getOperationNote(), actor.id());
            trips.event(
                connection,
                id,
                trip.getCarrierId(),
                trip.getScopeId(),
                event,
                body.getStopOrder(),
                body.getNote().strip(),
                actor.id()
            );
            return trips.assigned(connection, id, false, actor.id());
        });
    }
}
