package vn.vietroute.business.trip.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.business.trip.repo.TripScheduleRepo;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Điều hành duyệt lịch một lần; hệ thống bổ sung chuyến theo ngày và giữ nguyên giao dịch đã có. */
@Service
@RequiredArgsConstructor
public class DailyTripService {

    private final TripScheduleRepo schedules;
    private final TripRepo trips;
    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;
    private final Clock clock;

    /** Khóa lịch trước chuyến để đồng bộ nhiều node; chỉ nguồn đã công bố, thời lượng dưới một ngày. */
    public TripResponse configure(long id, TripRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getDailyService() != null && body.getVersion() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            schedules.lockSchedule();
            TripResponse trip = trips.find(id, true, true);
            BusinessRules.require(trip.getVersion() == body.getVersion(), ErrorCode.STALE_VERSION);
            BusinessRules.require(trip.getServiceSourceId() == null, ErrorCode.TRIP_STATE_CONFLICT);
            if (body.getDailyService()) {
                BusinessRules.require(
                    trip.getStatus() != TripStatus.DRAFT &&
                        trip.getStatus() != TripStatus.CANCELLED &&
                        Duration.between(trip.getDepartureAt(), trip.getEndAt()).compareTo(Duration.ofDays(1)) < 0,
                    ErrorCode.TRIP_STATE_CONFLICT
                );
                trips.requireStopsActive(id);
                trips.assets(trip.getCarrierId(), trip.getScopeId(), trip.getVehicleId(), trip.getDriverId());
                carriers.requireDriverUser(trip.getDriverUserId(), trip.getScopeId());
                BusinessRules.require(
                    !schedules.conflicts(
                        id,
                        trip.getVehicleId(),
                        trip.getDriverId(),
                        trip.getDriverUserId(),
                        trip.getDepartureAt(),
                        trip.getEndAt()
                    ),
                    ErrorCode.TRIP_SCHEDULE_CONFLICT
                );
            }
            BusinessRules.require(
                schedules.setDaily(id, body.getVersion(), body.getDailyService(), actor.id()) == 1,
                ErrorCode.STALE_VERSION
            );
            trips.event(
                id,
                trip.getCarrierId(),
                trip.getScopeId(),
                "NOTICE",
                null,
                body.getDailyService() ? "Bật lịch chạy hằng ngày" : "Dừng sinh chuyến hằng ngày",
                actor.id()
            );
            return trips.find(id, false, true);
        });
    }

    /** Bổ sung tự động sau startup và mỗi giờ; nhiều node dùng cùng khóa transaction PostgreSQL. */
    @Scheduled(initialDelay = 30000, fixedDelay = 3600000)
    public void replenish() {
        ensure(null);
    }

    /** Tìm ngày xa tạo đúng ngày đó từ lịch được duyệt, giới hạn cửa sổ đặt trước hai năm. */
    public void ensure(LocalDate requested) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(Business.TIME_ZONE)));
        BusinessRules.require(requested == null || requested.isBefore(today.plusDays(730)), ErrorCode.INVALID_REQUEST);
        if (requested != null && requested.isBefore(today)) return;
        transactions.execute(connection -> {
            schedules.lockSchedule();
            generate(requested);
            return null;
        });
    }

    /** Khóa tài nguyên cùng thứ tự với mở chuyến thủ công; một statement ghi toàn bộ snapshot/chặng/history. */
    private void generate(LocalDate requested) {
        schedules.lockVehicles();
        schedules.lockDrivers();
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        LocalDate from = requested == null ? LocalDate.now(clock.withZone(zone)) : requested;
        schedules.materialize(
            from.minusDays(1).atStartOfDay(zone).toInstant(),
            from
                .plusDays(requested == null ? 31 : 1)
                .atStartOfDay(zone)
                .toInstant()
        );
    }
}
