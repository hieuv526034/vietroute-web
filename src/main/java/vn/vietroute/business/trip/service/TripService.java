package vn.vietroute.business.trip.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.repository.BookingLifecycleRepository;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.business.trip.dto.request.TripAssignmentRequest;
import vn.vietroute.business.trip.dto.request.TripChoiceRequest;
import vn.vietroute.business.trip.dto.request.TripCreateRequest;
import vn.vietroute.business.trip.dto.request.TripNoticeRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.request.TripStateRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.business.trip.repository.TripTemplateRepository;
import vn.vietroute.business.trip.repository.TripTemplateRepository.Assets;
import vn.vietroute.business.trip.repository.TripTemplateRepository.SourceStop;
import vn.vietroute.business.trip.repository.TripTemplateRepository.Template;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Điều hành chuyến theo scope: draft, snapshot, phân công và mở/hủy không cho bỏ qua đồ thị trạng thái. */
@Service
@RequiredArgsConstructor
public class TripService {

    private final TrackingRepository tracking;

    private final TripRepository trips;
    private final TripTemplateRepository templates;
    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;
    private final BookingLifecycleRepository bookings;

    /** Danh sách staff lọc tại DB trước count/phân trang. */
    public PageResponse<TripResponse> page(TripSearchRequest search) {
        return trips.page(search);
    }

    /** READ độc lập VIEW; ID ngoài quyền là 404. */
    public TripResponse find(long id) {
        return transactions.execute(connection -> trips.find(connection, id, false, true));
    }

    /** tripId=0 chọn CREATE, ID có sẵn chọn UPDATE và buộc picker về chính nhà xe của trip. */
    public PageResponse<TripChoiceResponse> choices(long tripId, TripChoiceRequest body) {
        return transactions.execute(connection -> {
            if (tripId > 0) {
                TripResponse trip = trips.find(connection, tripId, false, false);
                body.setCarrierId(trip.carrierId());
            }
            return templates.choices(connection, body);
        });
    }

    /** Khóa nhà xe → mẫu → xe → tài xế → account, tạo snapshot/segments/history nguyên tử. */
    public TripResponse create(TripCreateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Instant start = future(body.getDepartureAt());
        body.setDepartureAt(start);
        return transactions.execute(connection -> {
            Template initial = templates.template(connection, body.getRouteId(), null, false);
            Carrier carrier = carriers.carrier(connection, initial.carrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            Template template = templates.template(connection, body.getRouteId(), carrier.getId(), true);
            validateTemplate(template);
            Assets assets = templates.assets(
                connection,
                carrier.getId(),
                carrier.getScopeId(),
                body.getVehicleId(),
                body.getDriverId()
            );
            carriers.requireDriverUser(connection, assets.driverUserId(), carrier.getScopeId());
            BusinessRules.require(
                body.getCapacity() > 0 && body.getCapacity() <= assets.capacity(),
                ErrorCode.TRIP_INVALID
            );
            Instant end = start.plusSeconds(
                template
                    .stops()
                    .get(template.stops().size() - 1)
                    .arrival() * 60L
            );
            long id = trips.insert(connection, body, template, assets, end, actor.id());
            return trips.find(connection, id, false, true);
        });
    }

    /** Mở khóa trip trước assets; hai publish cạnh tranh không thể vượt qua cùng kiểm tra lịch. */
    public TripResponse state(long id, TripStateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            TripResponse trip = trips.find(connection, id, true, true);
            version(trip, body.version());
            BusinessRules.require(
                body.status() == TripStatus.OPEN || body.status() == TripStatus.CANCELLED,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            if (body.status() == TripStatus.OPEN) {
                BusinessRules.require(trip.status() == TripStatus.DRAFT, ErrorCode.TRIP_STATE_CONFLICT);
                future(trip.departureAt());
                Carrier carrier = carriers.carrier(connection, trip.carrierId(), true);
                BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
                Template template = templates.template(connection, trip.routeId(), trip.carrierId(), true);
                validateTemplate(template);
                trips.requireStopsActive(connection, id);
                // Chỉ kiểm danh mục còn dùng; giữ nguyên tên/giờ/giá snapshot ngay cả mẫu vừa sửa.
                Assets assets = templates.assets(
                    connection,
                    trip.carrierId(),
                    trip.scopeId(),
                    trip.vehicleId(),
                    trip.driverId()
                );
                carriers.requireDriverUser(connection, assets.driverUserId(), trip.scopeId());
                BusinessRules.require(
                    assets.driverUserId() == trip.driverUserId() && trip.capacity() <= assets.capacity(),
                    ErrorCode.TRIP_INVALID
                );
                templates.requireAvailable(connection, id, assets, trip.departureAt(), trip.endAt(), false);
            } else {
                BusinessRules.require(
                    (trip.status() == TripStatus.DRAFT || trip.status() == TripStatus.OPEN) &&
                        body.note() != null &&
                        !body.note().isBlank(),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
            }
            if (body.status() == TripStatus.CANCELLED) bookings.cancelTrip(
                connection,
                id,
                body.note().strip(),
                actor.id()
            );
            trips.state(connection, trip, body.status(), body.note().strip(), actor.id());
            if (body.status() == TripStatus.CANCELLED) tracking.stop(connection, id, actor.id());
            trips.event(
                connection,
                id,
                trip.carrierId(),
                trip.scopeId(),
                body.status() == TripStatus.OPEN ? "OPENED" : "CANCELLED",
                null,
                body.note().strip(),
                actor.id()
            );
            return trips.find(connection, id, false, true);
        });
    }

    /** Đổi xe/tài xế cùng nhà xe; OPEN giữ lịch, DRAFT được dịch lịch nhưng không đổi snapshot giá/điểm. */
    public TripResponse assignment(long id, TripAssignmentRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            TripResponse trip = trips.find(connection, id, true, true);
            version(trip, body.version());
            BusinessRules.require(
                trip.status() == TripStatus.DRAFT || trip.status() == TripStatus.OPEN,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            Carrier carrier = carriers.carrier(connection, trip.carrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            Instant start =
                body.departureAt() == null ? trip.departureAt() : body.departureAt().truncatedTo(ChronoUnit.SECONDS);
            BusinessRules.require(
                trip.status() == TripStatus.DRAFT || start.equals(trip.departureAt()),
                ErrorCode.TRIP_STATE_CONFLICT
            );
            future(start);
            Instant end = start.plus(Duration.between(trip.departureAt(), trip.endAt()));
            Assets assets = templates.assets(
                connection,
                trip.carrierId(),
                trip.scopeId(),
                body.vehicleId(),
                body.driverId()
            );
            carriers.requireDriverUser(connection, assets.driverUserId(), trip.scopeId());
            BusinessRules.require(
                body.capacity() > 0 &&
                    body.capacity() <= assets.capacity() &&
                    body.capacity() >= trips.peakReserved(connection, id),
                ErrorCode.TRIP_INVALID
            );
            if (trip.status() == TripStatus.OPEN) templates.requireAvailable(connection, id, assets, start, end, false);
            trips.assignment(connection, trip, assets, body.capacity(), start, end, actor.id());
            tracking.stop(connection, id, actor.id());
            trips.event(
                connection,
                id,
                trip.carrierId(),
                trip.scopeId(),
                "ASSIGNED",
                null,
                body.note().strip(),
                actor.id()
            );
            return trips.find(connection, id, false, true);
        });
    }

    /** Giữ trạng thái OPEN/IN_PROGRESS, chỉ thay thông báo và ghi NOTICE cùng transaction. */
    public TripResponse notice(long id, TripNoticeRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.note() != null && !body.note().isBlank(), ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            TripResponse trip = trips.find(connection, id, true, true);
            version(trip, body.version());
            BusinessRules.require(
                trip.status() == TripStatus.OPEN || trip.status() == TripStatus.IN_PROGRESS,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            trips.state(connection, trip, trip.status(), body.note().strip(), actor.id());
            trips.event(
                connection,
                id,
                trip.carrierId(),
                trip.scopeId(),
                "NOTICE",
                null,
                body.note().strip(),
                actor.id()
            );
            return trips.find(connection, id, false, true);
        });
    }

    private Instant future(Instant value) {
        Instant now = Instant.now();
        Instant normalized = value == null ? null : value.truncatedTo(ChronoUnit.SECONDS);
        BusinessRules.require(
            normalized != null && normalized.isAfter(now) && normalized.isBefore(now.plus(730, ChronoUnit.DAYS)),
            ErrorCode.TRIP_INVALID
        );
        return normalized;
    }

    private void version(TripResponse trip, Long version) {
        BusinessRules.require(version != null && trip.version() == version, ErrorCode.STALE_VERSION);
    }

    /** Chống nguồn dữ liệu không hợp lệ từ SQL vận hành; không chỉ dựa form ROUTE01. */
    private void validateTemplate(Template template) {
        BusinessRules.require(
            template.active() && template.stops().size() >= 2 && template.stops().size() <= 32,
            ErrorCode.TRIP_INVALID
        );
        int previous = -1;
        int order = 0;
        for (SourceStop stop : template.stops()) {
            BusinessRules.require(
                stop.active() &&
                    stop.order() == ++order &&
                    stop.arrival() > previous &&
                    stop.departure() >= stop.arrival(),
                ErrorCode.TRIP_INVALID
            );
            previous = stop.departure();
        }
    }
}
