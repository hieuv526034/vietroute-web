package vn.vietroute.business.tracking.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Staff chỉ xem vị trí chuyến có TRIP_READ trong scope; không có quyền phát mẫu thay tài xế. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.TRIP)
public class StaffTrackingService {

    private final TripRepository trips;
    private final TrackingRepository tracking;
    private final JdbcTransactions transactions;
    private final TrackingSettings settings;
    private final Clock clock;

    @AccessOperation(action = PermissionAction.READ)
    public TrackingResponse read(long id) {
        return transactions.execute(connection -> {
            trips.find(connection, id, PermissionAction.READ, false, false);
            return tracking.view(connection, id, Groups.TRIP, null, null, settings.isEnabled(), clock.instant());
        });
    }
}
