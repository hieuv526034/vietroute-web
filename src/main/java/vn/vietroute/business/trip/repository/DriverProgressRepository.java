package vn.vietroute.business.trip.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Actual timestamp chỉ server ghi sau khi khóa trip và kiểm đúng tài xế/điểm kế tiếp. */
@Repository
public class DriverProgressRepository {

    /** Cập nhật đúng điểm cùng parent/carrier/scope; không nhận mốc thời gian từ thiết bị. */
    public void mark(Connection connection, TripResponse trip, int order, boolean arrival, Instant now, long actor)
        throws SQLException {
        String column = arrival ? "actual_arrival_at" : "actual_departure_at";
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_stops SET " +
                    column +
                    "=?,updated_at=CURRENT_TIMESTAMP,updated_by=?,version=version+1 WHERE trip_id=? AND carrier_id=? AND scope_id=? AND stop_order=? AND " +
                    column +
                    " IS NULL"
            )
        ) {
            query.setTimestamp(1, Timestamp.from(now));
            query.setLong(2, actor);
            query.setLong(3, trip.id());
            query.setLong(4, trip.carrierId());
            query.setLong(5, trip.scopeId());
            query.setInt(6, order);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.TRIP_STATE_CONFLICT);
        }
    }
}
