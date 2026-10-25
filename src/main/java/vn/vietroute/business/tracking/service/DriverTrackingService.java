package vn.vietroute.business.tracking.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.tracking.dto.request.TrackingSampleRequest;
import vn.vietroute.business.tracking.dto.request.TrackingStartRequest;
import vn.vietroute.business.tracking.dto.request.TrackingStopRequest;
import vn.vietroute.business.tracking.dto.response.TrackingPointResponse;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.dto.response.TrackingSessionResponse;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Consent chỉ tài xế được giao, khóa trip trước state/sample; vị trí không thay actual chặng hoặc trạng thái đơn. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.DRIVER_TRIP)
public class DriverTrackingService {

    private final TripRepository trips;
    private final TrackingRepository tracking;
    private final JdbcTransactions transactions;
    private final TrackingSettings settings;
    private final Clock clock;

    /** VIEW/READ hiện hành của chuyến được giao; không trả nonce để tab khác tự tiếp tục consent. */
    @AccessOperation(action = PermissionAction.READ)
    public TrackingResponse read(long id, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, PermissionAction.READ, false, actor.id());
            return tracking.view(connection, id, Groups.DRIVER_TRIP, null, null, settings.isEnabled(), clock.instant());
        });
    }

    /** Bật trước khởi hành tối đa 30 phút hoặc khi đang chạy; mỗi lần bật cấp nonce mới và xóa snapshot cũ. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TrackingSessionResponse start(long id, TrackingStartRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(settings.isEnabled(), ErrorCode.TRACKING_DISABLED);
        return transactions.execute(connection -> {
            var trip = trips.assigned(connection, id, PermissionAction.UPDATE, true, actor.id());
            BusinessRules.require(
                body.tripVersion() != null && body.tripVersion() == trip.version(),
                ErrorCode.STALE_VERSION
            );
            Instant now = clock.instant();
            shareable(trip, now);
            UUID key = UUID.randomUUID();
            tracking.start(connection, trip, key, now, actor.id());
            return new TrackingSessionResponse(
                key,
                now,
                TrackingSettings.MIN_SAMPLE_SECONDS,
                TrackingSettings.LEASE_SECONDS
            );
        });
    }

    /** Stop cùng nonce lặp lại không tạo mutation; nonce cũ không được tắt consent mới trên thiết bị khác. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TrackingResponse stop(long id, TrackingStopRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, PermissionAction.UPDATE, true, actor.id());
            var state = tracking.state(connection, id);
            BusinessRules.require(
                state != null && state.key().equals(body.sessionKey()) && state.driverUserId() == actor.id(),
                ErrorCode.TRACKING_SESSION
            );
            if (state.sharing()) tracking.stop(connection, id, actor.id());
            return new TrackingResponse("OFF", "UNCONFIGURED", clock.instant(), null, null, List.of());
        });
    }

    /** Mẫu cũ/future/sai thứ tự bị chặn; replay chính xác không làm mới lease hoặc thêm trace. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public TrackingPointResponse sample(long id, TrackingSampleRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(settings.isEnabled(), ErrorCode.TRACKING_DISABLED);
        return transactions.execute(connection -> {
            var trip = trips.assigned(connection, id, PermissionAction.UPDATE, true, actor.id());
            Instant now = clock.instant();
            valid(body, now);
            shareable(trip, now);
            var state = tracking.state(connection, id);
            BusinessRules.require(
                state != null &&
                    state.sharing() &&
                    state.key().equals(body.sessionKey()) &&
                    state.driverUserId() == actor.id(),
                ErrorCode.TRACKING_SESSION
            );
            Instant last = state.latest() == null ? state.startedAt() : state.latest().receivedAt();
            BusinessRules.require(
                !now.isAfter(last.plusSeconds(TrackingSettings.LEASE_SECONDS)),
                ErrorCode.TRACKING_SESSION
            );
            if (state.latest() != null) {
                var point = state.latest();
                BusinessRules.require(
                    !body.measuredAt().isBefore(point.measuredAt()),
                    ErrorCode.TRACKING_SAMPLE_INVALID
                );
                if (body.measuredAt().equals(point.measuredAt())) {
                    BusinessRules.require(
                        body.latitude().compareTo(point.latitude()) == 0 &&
                            body.longitude().compareTo(point.longitude()) == 0 &&
                            body.accuracy().compareTo(point.accuracy()) == 0,
                        ErrorCode.TRACKING_SAMPLE_INVALID
                    );
                    return point;
                }
                BusinessRules.require(
                    !now.isBefore(point.receivedAt().plusSeconds(TrackingSettings.MIN_SAMPLE_SECONDS)),
                    ErrorCode.TRACKING_THROTTLED
                );
            }
            tracking.sample(connection, trip, body, now, actor.id());
            return tracking.state(connection, id).latest();
        });
    }

    private static void shareable(TripResponse trip, Instant now) {
        BusinessRules.require(
            trip.status() == TripStatus.IN_PROGRESS ||
                (trip.status() == TripStatus.OPEN &&
                    !now.isBefore(trip.departureAt().minusSeconds(1800)) &&
                    now.isBefore(trip.endAt().plusSeconds(86400))),
            ErrorCode.TRACKING_SESSION
        );
    }

    private static void valid(TrackingSampleRequest body, Instant now) {
        BusinessRules.require(
            body.sessionKey() != null &&
                coordinate(body.latitude(), 90, 6) &&
                coordinate(body.longitude(), 180, 6) &&
                body.accuracy() != null &&
                body.accuracy().signum() >= 0 &&
                body.accuracy().compareTo(BigDecimal.valueOf(100000)) <= 0 &&
                body.accuracy().scale() <= 2 &&
                body.measuredAt() != null &&
                body.measuredAt().getNano() % 1000000 == 0 &&
                !body.measuredAt().isBefore(now.minusSeconds(TrackingSettings.MAX_SAMPLE_AGE_SECONDS)) &&
                !body.measuredAt().isAfter(now.plusSeconds(TrackingSettings.FUTURE_SKEW_SECONDS)),
            ErrorCode.TRACKING_SAMPLE_INVALID
        );
    }

    private static boolean coordinate(BigDecimal value, int maximum, int scale) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(maximum)) <= 0 && value.scale() <= scale;
    }
}
