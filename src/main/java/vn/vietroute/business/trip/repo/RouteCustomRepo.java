package vn.vietroute.business.trip.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.trip.dto.request.RouteRequest;
import vn.vietroute.business.trip.dto.request.RouteStopRequest;

/** SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ. */
@Repository
public class RouteCustomRepo {

    /** Thay mẫu điểm nguyên tử; chuyến dùng snapshot độc lập nên không tham chiếu ID của mẫu điểm. */
    public void replaceStops(Connection connection, long id, long scopeId, RouteRequest body, long actor)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement("DELETE FROM carrier_route_stops WHERE route_id=?")
        ) {
            query.setLong(1, id);
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_route_stops(route_id, carrier_id, scope_id, location_id, stop_order,
                    arrival_offset_minutes, departure_offset_minutes, cumulative_fare, pickup_allowed,
                    dropoff_allowed, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            int order = 0;
            for (RouteStopRequest stop : body.getStops()) {
                query.setLong(1, id);
                query.setLong(2, body.getCarrierId());
                query.setLong(3, scopeId);
                query.setLong(4, stop.getLocationId());
                query.setInt(5, ++order);
                query.setInt(6, stop.getArrivalOffsetMinutes());
                query.setInt(7, stop.getDepartureOffsetMinutes());
                query.setBigDecimal(8, stop.getCumulativeFare());
                query.setBoolean(9, stop.isPickupAllowed());
                query.setBoolean(10, stop.isDropoffAllowed());
                query.setLong(11, actor);
                query.setLong(12, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }
}
