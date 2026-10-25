package vn.vietroute.business.trip.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.booking.dto.response.DriverPassengerResponse;
import vn.vietroute.business.booking.repository.BookingLifecycleRepository;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.business.trip.dto.request.DriverProgressRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.business.trip.repository.DriverProgressRepository;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.business.trip.repository.TripTemplateRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** DRIVER chỉ thao tác chuyến được giao; tiến độ có thứ tự, thời gian server và lịch sử nguyên tử. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.DRIVER_TRIP)
public class DriverTripService {

    private final TripRepository trips;
    private final TripTemplateRepository templates;
    private final CarrierRepository carriers;
    private final DriverProgressRepository progress;
    private final JdbcTransactions transactions;
    private final BookingLifecycleRepository bookings;
    private final BookingRepository passengerBookings;
    private final TrackingRepository tracking;

    /** Driver không cần BOOKING_READ; chỉ projection đón/trả sau kiểm chính phân công hiện hành. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<DriverPassengerResponse> passengers(long id, PageRequest page, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, PermissionAction.READ, false, actor.id());
            return passengerBookings.passengers(connection, id, page);
        });
    }

    /** SQL count/page cùng ràng buộc người được phân công, không có bypass ADMIN. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<TripResponse> page(TripSearchRequest search, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return trips.assignedPage(search, actor.id());
    }

    /** ID chuyến người khác trả 404, kể cả cùng nhà xe. */
    @AccessOperation(action = PermissionAction.READ)
    public TripResponse find(long id, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection ->
            trips.assigned(connection, id, PermissionAction.READ, false, actor.id())
        );
    }

    /** Khóa trip trước, kiểm trạng thái/điểm, ghi actual/state/event cùng transaction. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TripResponse update(long id, DriverProgressRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(body.action() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            var trip = trips.assigned(connection, id, PermissionAction.UPDATE, true, actor.id());
            BusinessRules.require(body.version() != null && body.version() == trip.version(), ErrorCode.STALE_VERSION);
            var stops = trip.stops();
            int count = stops.size();
            Instant now = Instant.now();
            BusinessRules.require(
                count >= 2 && body.stopOrder() >= 1 && body.stopOrder() <= count,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            var stop = stops.get(body.stopOrder() - 1);
            TripStatus state = trip.status();
            String event;
            switch (body.action()) {
                case START -> {
                    BusinessRules.require(
                        state == TripStatus.OPEN &&
                            body.stopOrder() == 1 &&
                            stop.actualArrivalAt() == null &&
                            !now.isBefore(trip.departureAt().minusSeconds(1800)),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    var assets = templates.assets(
                        connection,
                        trip.carrierId(),
                        trip.scopeId(),
                        trip.vehicleId(),
                        trip.driverId()
                    );
                    carriers.requireDriverUser(connection, assets.driverUserId(), trip.scopeId());
                    BusinessRules.require(
                        assets.driverUserId() == actor.id() && assets.capacity() >= trip.capacity(),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    templates.requireAvailable(connection, id, assets, trip.departureAt(), trip.endAt(), true);
                    progress.mark(connection, trip, 1, true, now, actor.id());
                    progress.mark(connection, trip, 1, false, now, actor.id());
                    state = TripStatus.IN_PROGRESS;
                    event = "STARTED";
                }
                case ARRIVE -> {
                    BusinessRules.require(
                        state == TripStatus.IN_PROGRESS &&
                            body.stopOrder() > 1 &&
                            stop.actualArrivalAt() == null &&
                            stops.get(body.stopOrder() - 2).actualDepartureAt() != null &&
                            !now.isBefore(stops.get(body.stopOrder() - 2).actualDepartureAt()),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    progress.mark(connection, trip, body.stopOrder(), true, now, actor.id());
                    event = "ARRIVED";
                }
                case DEPART -> {
                    BusinessRules.require(
                        state == TripStatus.IN_PROGRESS &&
                            body.stopOrder() > 1 &&
                            body.stopOrder() < count &&
                            stop.actualArrivalAt() != null &&
                            stop.actualDepartureAt() == null &&
                            !now.isBefore(stop.actualArrivalAt()),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    progress.mark(connection, trip, body.stopOrder(), false, now, actor.id());
                    event = "DEPARTED";
                }
                case COMPLETE -> {
                    BusinessRules.require(
                        state == TripStatus.IN_PROGRESS &&
                            body.stopOrder() == count &&
                            stop.actualArrivalAt() != null &&
                            stop.actualDepartureAt() == null &&
                            !now.isBefore(stop.actualArrivalAt()),
                        ErrorCode.TRIP_STATE_CONFLICT
                    );
                    progress.mark(connection, trip, count, false, now, actor.id());
                    state = TripStatus.COMPLETED;
                    event = "COMPLETED";
                }
                default -> throw new IllegalArgumentException("Hành động tiến độ chưa hỗ trợ");
            }
            if (
                body.action() == DriverProgressRequest.Action.START ||
                body.action() == DriverProgressRequest.Action.DEPART
            ) bookings.departed(connection, id, body.stopOrder(), actor.id());
            if (body.action() == DriverProgressRequest.Action.ARRIVE) bookings.arrived(
                connection,
                id,
                body.stopOrder(),
                actor.id()
            );
            trips.state(connection, trip, state, trip.operationNote(), actor.id());
            if (state == TripStatus.COMPLETED) tracking.stop(connection, id, actor.id());
            trips.event(
                connection,
                id,
                trip.carrierId(),
                trip.scopeId(),
                event,
                body.stopOrder(),
                body.note().strip(),
                actor.id()
            );
            return trips.assigned(connection, id, PermissionAction.UPDATE, false, actor.id());
        });
    }
}
