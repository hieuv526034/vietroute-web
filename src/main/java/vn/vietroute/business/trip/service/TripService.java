package vn.vietroute.business.trip.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.trip.dto.request.TripAssignmentRequest;
import vn.vietroute.business.trip.dto.request.TripChoiceRequest;
import vn.vietroute.business.trip.dto.request.TripCreateRequest;
import vn.vietroute.business.trip.dto.request.TripSearchRequest;
import vn.vietroute.business.trip.dto.request.TripStateRequest;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.business.trip.repository.TripTemplateRepository;
import vn.vietroute.business.trip.repository.TripTemplateRepository.Template;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Điều hành chuyến theo scope: draft, snapshot, phân công và mở/hủy không cho bỏ qua đồ thị trạng thái. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.TRIP)
public class TripService {

    private final TripRepository trips;
    private final TripTemplateRepository templates;
    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;

    /** Danh sách staff lọc tại DB trước count/phân trang. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<TripResponse> page(TripSearchRequest search) {
        return trips.page(search);
    }

    /** READ độc lập VIEW; ID ngoài quyền là 404. */
    @AccessOperation(action = PermissionAction.READ)
    public TripResponse find(long id) {
        return transactions.execute(connection -> trips.find(connection, id, PermissionAction.READ, false, true));
    }

    /** tripId=0 chọn CREATE, ID có sẵn chọn UPDATE và buộc picker về chính nhà xe của trip. */
    @AccessOperation(createArgument = "tripId")
    public PageResponse<TripChoiceResponse> choices(long tripId, TripChoiceRequest body) {
        return transactions.execute(connection -> {
            var action = tripId == 0 ? PermissionAction.CREATE : PermissionAction.UPDATE;
            if (tripId > 0) {
                var trip = trips.find(connection, tripId, action, false, false);
                body.setCarrierId(trip.carrierId());
            }
            return templates.choices(connection, body, action);
        });
    }

    /** Khóa nhà xe → mẫu → xe → tài xế → account, tạo snapshot/segments/history nguyên tử. */
    @AccessOperation(action = PermissionAction.CREATE)
    public TripResponse create(TripCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        var start = future(body.getDepartureAt());
        body.setDepartureAt(start);
        return transactions.execute(connection -> {
            var initial = templates.template(connection, body.getRouteId(), null, false);
            var carrier = carriers.carrier(connection, initial.carrierId(), Groups.TRIP, PermissionAction.CREATE, true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            var template = templates.template(connection, body.getRouteId(), carrier.getId(), true);
            validateTemplate(template);
            var assets = templates.assets(
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
            var end = start.plusSeconds(
                template
                    .stops()
                    .get(template.stops().size() - 1)
                    .arrival() * 60L
            );
            long id = trips.insert(connection, body, template, assets, end, actor.id());
            return trips.find(connection, id, PermissionAction.CREATE, false, true);
        });
    }

    /** Mở khóa trip trước assets; hai publish cạnh tranh không thể vượt qua cùng kiểm tra lịch. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TripResponse state(long id, TripStateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            var trip = trips.find(connection, id, PermissionAction.UPDATE, true, true);
            version(trip, body.version());
            BusinessRules.require(
                body.status() == TripStatus.OPEN || body.status() == TripStatus.CANCELLED,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            if (body.status() == TripStatus.OPEN) {
                BusinessRules.require(trip.status() == TripStatus.DRAFT, ErrorCode.TRIP_STATE_CONFLICT);
                future(trip.departureAt());
                var carrier = carriers.carrier(
                    connection,
                    trip.carrierId(),
                    Groups.TRIP,
                    PermissionAction.UPDATE,
                    true
                );
                BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
                var template = templates.template(connection, trip.routeId(), trip.carrierId(), true);
                validateTemplate(template);
                trips.requireStopsActive(connection, id);
                // Chỉ kiểm danh mục còn dùng; giữ nguyên tên/giờ/giá snapshot ngay cả mẫu vừa sửa.
                var assets = templates.assets(
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
            trips.state(connection, trip, body.status(), body.note().strip(), actor.id());
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
            return trips.find(connection, id, PermissionAction.UPDATE, false, true);
        });
    }

    /** Đổi xe/tài xế cùng nhà xe; OPEN giữ lịch, DRAFT được dịch lịch nhưng không đổi snapshot giá/điểm. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TripResponse assignment(long id, TripAssignmentRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            var trip = trips.find(connection, id, PermissionAction.UPDATE, true, true);
            version(trip, body.version());
            BusinessRules.require(
                trip.status() == TripStatus.DRAFT || trip.status() == TripStatus.OPEN,
                ErrorCode.TRIP_STATE_CONFLICT
            );
            var carrier = carriers.carrier(connection, trip.carrierId(), Groups.TRIP, PermissionAction.UPDATE, true);
            BusinessRules.require(carrier.isActive(), ErrorCode.TRIP_STATE_CONFLICT);
            Instant start =
                body.departureAt() == null ? trip.departureAt() : body.departureAt().truncatedTo(ChronoUnit.SECONDS);
            BusinessRules.require(
                trip.status() == TripStatus.DRAFT || start.equals(trip.departureAt()),
                ErrorCode.TRIP_STATE_CONFLICT
            );
            future(start);
            Instant end = start.plus(Duration.between(trip.departureAt(), trip.endAt()));
            var assets = templates.assets(
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
            return trips.find(connection, id, PermissionAction.UPDATE, false, true);
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
        for (var stop : template.stops()) {
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
