package vn.vietroute.business.tracking.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.tracking.dto.request.TrackingRequest;
import vn.vietroute.business.tracking.dto.response.TrackingPointResponse;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.dto.response.TrackingSessionResponse;
import vn.vietroute.business.tracking.repo.TrackingRepo;
import vn.vietroute.business.tracking.repo.TrackingRepo.State;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Consent chỉ tài xế được giao, khóa trip trước state/sample; vị trí không thay actual chặng hoặc trạng thái đơn. */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final BookingRepo bookings;
    private final TripRepo trips;
    private final TrackingRepo tracking;
    private final JdbcTransactions transactions;
    private final TrackingSettings settings;
    private final Clock clock;

    /** Target GET hiện hành của chuyến được giao; không trả nonce để tab khác tự tiếp tục consent. */
    public TrackingResponse read(long id, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, false, actor.id());
            return tracking.view(connection, id, true, null, null, settings.isEnabled(), clock.instant());
        });
    }

    /** Bật trước khởi hành tối đa 30 phút hoặc khi đang chạy; mỗi lần bật cấp nonce mới và xóa snapshot cũ. */
    public TrackingSessionResponse start(long id, TrackingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(settings.isEnabled(), ErrorCode.TRACKING_DISABLED);
        return transactions.execute(connection -> {
            TripResponse trip = trips.assigned(connection, id, true, actor.id());
            BusinessRules.require(
                body.getTripVersion() != null && body.getTripVersion() == trip.getVersion(),
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
    public TrackingResponse stop(long id, TrackingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            trips.assigned(connection, id, true, actor.id());
            State state = tracking.state(connection, id);
            BusinessRules.require(
                state != null && state.getKey().equals(body.getSessionKey()) && state.getDriverUserId() == actor.id(),
                ErrorCode.TRACKING_SESSION
            );
            if (state.isSharing()) tracking.stop(connection, id, actor.id());
            return new TrackingResponse("OFF", "UNCONFIGURED", clock.instant(), null, null, List.of());
        });
    }

    /** Mẫu cũ/future/sai thứ tự bị chặn; replay chính xác không làm mới lease hoặc thêm trace. */
    public TrackingPointResponse sample(long id, TrackingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(settings.isEnabled(), ErrorCode.TRACKING_DISABLED);
        return transactions.execute(connection -> {
            TripResponse trip = trips.assigned(connection, id, true, actor.id());
            Instant now = clock.instant();
            valid(body, now);
            shareable(trip, now);
            State state = tracking.state(connection, id);
            BusinessRules.require(
                state != null &&
                    state.isSharing() &&
                    state.getKey().equals(body.getSessionKey()) &&
                    state.getDriverUserId() == actor.id(),
                ErrorCode.TRACKING_SESSION
            );
            Instant last = state.getLatest() == null ? state.getStartedAt() : state.getLatest().getReceivedAt();
            BusinessRules.require(
                !now.isAfter(last.plusSeconds(TrackingSettings.LEASE_SECONDS)),
                ErrorCode.TRACKING_SESSION
            );
            if (state.getLatest() != null) {
                TrackingPointResponse point = state.getLatest();
                BusinessRules.require(
                    !body.getMeasuredAt().isBefore(point.getMeasuredAt()),
                    ErrorCode.TRACKING_SAMPLE_INVALID
                );
                if (body.getMeasuredAt().equals(point.getMeasuredAt())) {
                    BusinessRules.require(
                        body.getLatitude().compareTo(point.getLatitude()) == 0 &&
                            body.getLongitude().compareTo(point.getLongitude()) == 0 &&
                            body.getAccuracy().compareTo(point.getAccuracy()) == 0,
                        ErrorCode.TRACKING_SAMPLE_INVALID
                    );
                    return point;
                }
                BusinessRules.require(
                    !now.isBefore(point.getReceivedAt().plusSeconds(TrackingSettings.MIN_SAMPLE_SECONDS)),
                    ErrorCode.TRACKING_THROTTLED
                );
            }
            tracking.sample(connection, trip, body, now, actor.id());
            return tracking.state(connection, id).getLatest();
        });
    }

    private static void shareable(TripResponse trip, Instant now) {
        BusinessRules.require(
            trip.getStatus() == TripStatus.IN_PROGRESS ||
                (trip.getStatus() == TripStatus.OPEN &&
                    !now.isBefore(trip.getDepartureAt().minusSeconds(1800)) &&
                    now.isBefore(trip.getEndAt().plusSeconds(86400))),
            ErrorCode.TRACKING_SESSION
        );
    }

    private static void valid(TrackingRequest body, Instant now) {
        BusinessRules.require(
            body.getSessionKey() != null &&
                coordinate(body.getLatitude(), 90, 6) &&
                coordinate(body.getLongitude(), 180, 6) &&
                body.getAccuracy() != null &&
                body.getAccuracy().signum() >= 0 &&
                body.getAccuracy().compareTo(BigDecimal.valueOf(100000)) <= 0 &&
                body.getAccuracy().scale() <= 2 &&
                body.getMeasuredAt() != null &&
                body.getMeasuredAt().getNano() % 1000000 == 0 &&
                !body.getMeasuredAt().isBefore(now.minusSeconds(TrackingSettings.MAX_SAMPLE_AGE_SECONDS)) &&
                !body.getMeasuredAt().isAfter(now.plusSeconds(TrackingSettings.FUTURE_SKEW_SECONDS)),
            ErrorCode.TRACKING_SAMPLE_INVALID
        );
    }

    private static boolean coordinate(BigDecimal value, int maximum, int scale) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(maximum)) <= 0 && value.scale() <= scale;
    }

    public TrackingResponse readStaff(long id) {
        return transactions.execute(connection -> {
            trips.find(connection, id, false, false);
            return tracking.view(connection, id, true, null, null, settings.isEnabled(), clock.instant());
        });
    }

    public TrackingResponse readCustomer(long bookingId, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), false);
            BookingResponse booking = bookings.find(connection, bookingId, actor.id(), false, false).getResponse();
            return tracking.view(
                connection,
                booking.getTripId(),
                false,
                bookingId,
                actor.id(),
                settings.isEnabled(),
                clock.instant()
            );
        });
    }
}
