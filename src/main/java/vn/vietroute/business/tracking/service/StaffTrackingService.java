package vn.vietroute.business.tracking.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.tracking.repository.TrackingRepository;
import vn.vietroute.business.trip.repository.TripRepository;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Staff chỉ xem vị trí chuyến có TRIP_READ trong scope; không có quyền phát mẫu thay tài xế. */
@Service
@RequiredArgsConstructor
public class StaffTrackingService {

    private final TripRepository trips;
    private final TrackingRepository tracking;
    private final JdbcTransactions transactions;
    private final TrackingSettings settings;
    private final Clock clock;

    public TrackingResponse read(long id) {
        return transactions.execute(connection -> {
            trips.find(connection, id, false, false);
            return tracking.view(connection, id, true, null, null, settings.isEnabled(), clock.instant());
        });
    }
}
