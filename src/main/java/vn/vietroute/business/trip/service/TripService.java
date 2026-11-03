package vn.vietroute.business.trip.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.search.TripChoiceSearch;
import vn.vietroute.business.trip.dto.search.TripSearch;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.business.trip.repo.TripRepo.Assets;
import vn.vietroute.business.trip.repo.TripRepo.SourceStop;
import vn.vietroute.business.trip.repo.TripRepo.Template;
import vn.vietroute.business.trip.repo.TripSegmentRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Điều hành chuyến theo scope: draft, snapshot, phân công và mở/hủy không cho bỏ qua đồ thị trạng thái. */
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepo trips;
    private final TripSegmentRepo segments;
    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;
    private final BookingRepo bookings;

    /** Danh sách staff lọc tại DB trước count/phân trang. */
    public PageResponse<TripResponse> page(TripSearch search) {
        return trips.page(search);
    }

    /** GET dữ liệu độc lập GET menu; ID ngoài quyền là 404. */
    @Transactional(readOnly = true)
    public TripResponse find(long id) {
        return trips.find(id, false, true);
    }

    /** tripId=0 chọn CREATE, ID có sẵn chọn UPDATE và buộc picker về chính nhà xe của trip. */
    @Transactional(readOnly = true)
    public PageResponse<TripChoiceResponse> choices(long tripId, TripChoiceSearch body) {
        if (tripId > 0) {
            TripResponse trip = trips.find(tripId, false, false);
            body.setCarrierId(trip.getCarrierId());
        }
        return trips.choices(body);
    }

    /** Khóa nhà xe → mẫu → xe → tài xế → account, tạo snapshot/segments/history nguyên tử. */
    public TripResponse create(TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Instant start = future(body.getDepartureAt());
        body.setDepartureAt(start);
        return transactions.execute(connection -> {
            Template initial = trips.template(body.getRouteId(), null, false);
            Carrier carrier = carriers.carrier(initial.getCarrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            Template template = trips.template(body.getRouteId(), carrier.getId(), true);
            validateTemplate(template);
            Assets assets = trips.assets(
                carrier.getId(),
                carrier.getScopeId(),
                body.getVehicleId(),
                body.getDriverId()
            );
            carriers.requireDriverUser(assets.getDriverUserId(), carrier.getScopeId());
            BusinessRules.require(
                body.getCapacity() > 0 && body.getCapacity() <= assets.getCapacity(),
                ErrorCode.TRIP_INVALID
            );
            Instant end = start.plusSeconds(
                template
                    .getStops()
                    .get(template.getStops().size() - 1)
                    .getArrival() * 60L
            );
            long id = trips.insert(connection, body, template, assets, end, actor.id());
            return trips.find(id, false, true);
        });
    }

    /** Mở khóa trip trước assets; hai publish cạnh tranh không thể vượt qua cùng kiểm tra lịch. */
    public TripResponse state(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            TripResponse trip = trips.find(id, true, true);
            version(trip, body.getVersion());
            BusinessRules.require(
                body.getStatus() == TripStatus.OPEN || body.getStatus() == TripStatus.CANCELLED,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            if (body.getStatus() == TripStatus.OPEN) {
                BusinessRules.require(trip.getStatus() == TripStatus.DRAFT, ErrorCode.TRIP_STATE_CONFLICT);
                future(trip.getDepartureAt());
                Carrier carrier = carriers.carrier(trip.getCarrierId(), true);
                BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
                Template template = trips.template(trip.getRouteId(), trip.getCarrierId(), true);
                validateTemplate(template);
                trips.requireStopsActive(id);
                // Chỉ kiểm danh mục còn dùng; giữ nguyên tên/giờ/giá snapshot ngay cả mẫu vừa sửa.
                Assets assets = trips.assets(
                    trip.getCarrierId(),
                    trip.getScopeId(),
                    trip.getVehicleId(),
                    trip.getDriverId()
                );
                carriers.requireDriverUser(assets.getDriverUserId(), trip.getScopeId());
                BusinessRules.require(
                    assets.getDriverUserId() == trip.getDriverUserId() && trip.getCapacity() <= assets.getCapacity(),
                    ErrorCode.TRIP_INVALID
                );
                trips.requireAvailable(id, assets, trip.getDepartureAt(), trip.getEndAt(), false);
            } else {
                BusinessRules.require(
                    (trip.getStatus() == TripStatus.DRAFT || trip.getStatus() == TripStatus.OPEN) &&
                        body.getNote() != null &&
                        !body.getNote().isBlank(),
                    ErrorCode.TRIP_STATE_CONFLICT
                );
            }
            if (body.getStatus() == TripStatus.CANCELLED) bookings.cancelTrip(
                connection,
                id,
                body.getNote().strip(),
                actor.id()
            );
            trips.state(trip, body.getStatus(), body.getNote().strip(), actor.id());
            trips.event(
                id,
                trip.getCarrierId(),
                trip.getScopeId(),
                body.getStatus() == TripStatus.OPEN ? "OPENED" : "CANCELLED",
                null,
                body.getNote().strip(),
                actor.id()
            );
            return trips.find(id, false, true);
        });
    }

    /** Đổi xe/tài xế cùng nhà xe; OPEN giữ lịch, DRAFT được dịch lịch nhưng không đổi snapshot giá/điểm. */
    public TripResponse assignment(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            TripResponse trip = trips.find(id, true, true);
            BusinessRules.require(
                (body.getCode() == null || trip.getCode().equals(body.getCode())) &&
                    (body.getRouteId() == 0 || trip.getRouteId() == body.getRouteId()),
                ErrorCode.INVALID_REQUEST
            );
            version(trip, body.getVersion());
            BusinessRules.require(
                trip.getStatus() == TripStatus.DRAFT || trip.getStatus() == TripStatus.OPEN,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            Carrier carrier = carriers.carrier(trip.getCarrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            Instant start =
                body.getDepartureAt() == null
                    ? trip.getDepartureAt()
                    : body.getDepartureAt().truncatedTo(ChronoUnit.SECONDS);
            BusinessRules.require(
                trip.getStatus() == TripStatus.DRAFT || start.equals(trip.getDepartureAt()),
                ErrorCode.TRIP_STATE_CONFLICT
            );
            future(start);
            Instant end = start.plus(Duration.between(trip.getDepartureAt(), trip.getEndAt()));
            Assets assets = trips.assets(
                trip.getCarrierId(),
                trip.getScopeId(),
                body.getVehicleId(),
                body.getDriverId()
            );
            carriers.requireDriverUser(assets.getDriverUserId(), trip.getScopeId());
            BusinessRules.require(
                body.getCapacity() > 0 &&
                    body.getCapacity() <= assets.getCapacity() &&
                    body.getCapacity() >= segments.peakReserved(connection, id),
                ErrorCode.TRIP_INVALID
            );
            if (trip.getStatus() == TripStatus.OPEN) trips.requireAvailable(id, assets, start, end, false);
            trips.assignment(connection, trip, assets, body.getCapacity(), start, end, actor.id());
            trips.event(
                id,
                trip.getCarrierId(),
                trip.getScopeId(),
                "ASSIGNED",
                null,
                body.getNote().strip(),
                actor.id()
            );
            return trips.find(id, false, true);
        });
    }

    /** Giữ trạng thái OPEN/IN_PROGRESS, chỉ thay thông báo và ghi NOTICE cùng transaction. */
    @Transactional
    public TripResponse notice(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getNote() != null && !body.getNote().isBlank(), ErrorCode.INVALID_REQUEST);
        TripResponse trip = trips.find(id, true, true);
        version(trip, body.getVersion());
        BusinessRules.require(
            trip.getStatus() == TripStatus.OPEN || trip.getStatus() == TripStatus.IN_PROGRESS,
            ErrorCode.TRIP_STATE_CONFLICT
        );
        trips.state(trip, trip.getStatus(), body.getNote().strip(), actor.id());
        trips.event(id, trip.getCarrierId(), trip.getScopeId(), "NOTICE", null, body.getNote().strip(), actor.id());
        return trips.find(id, false, true);
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
        BusinessRules.require(version != null && trip.getVersion() == version, ErrorCode.STALE_VERSION);
    }

    /** Chống nguồn dữ liệu không hợp lệ từ SQL vận hành; không chỉ dựa form ROUTE01. */
    private void validateTemplate(Template template) {
        BusinessRules.require(
            template.isActive() && template.getStops().size() >= 2 && template.getStops().size() <= 32,
            ErrorCode.TRIP_INVALID
        );
        int previous = -1;
        int order = 0;
        for (SourceStop stop : template.getStops()) {
            BusinessRules.require(
                stop.isActive() &&
                    stop.getOrder() == ++order &&
                    stop.getArrival() > previous &&
                    stop.getDeparture() >= stop.getArrival(),
                ErrorCode.TRIP_INVALID
            );
            previous = stop.getDeparture();
        }
    }
}
