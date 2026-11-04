package vn.vietroute.business.trip.repo.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.trip.repo.TripRepo.SourceStop;
import vn.vietroute.business.trip.repo.TripRepo.Template;

/**
 * Ghi dữ liệu theo lô trên connection của giao dịch nghiệp vụ.
 */
@Repository
public class TripStopCustomRepo {

    public void insertBatch(Connection connection, long id, Template template, Instant departure, long actor)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO trip_stops(trip_id, carrier_id, scope_id, location_id, province_id, stop_order,
                    name_vi, name_en, address, latitude, longitude, scheduled_arrival_at, scheduled_departure_at,
                    cumulative_fare, pickup_allowed, dropoff_allowed, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
        ) {
            for (SourceStop stop : template.getStops()) {
                query.setLong(1, id);
                query.setLong(2, template.getCarrierId());
                query.setLong(3, template.getScopeId());
                query.setLong(4, stop.getLocationId());
                query.setLong(5, stop.getProvinceId());
                query.setInt(6, stop.getOrder());
                query.setString(7, stop.getNameVi());
                query.setString(8, stop.getNameEn());
                query.setString(9, stop.getAddress());
                query.setBigDecimal(10, stop.getLatitude());
                query.setBigDecimal(11, stop.getLongitude());
                query.setTimestamp(12, Timestamp.from(departure.plusSeconds(stop.getArrival() * 60L)));
                query.setTimestamp(13, Timestamp.from(departure.plusSeconds(stop.getDeparture() * 60L)));
                query.setBigDecimal(14, stop.getFare());
                query.setBoolean(15, stop.isPickup());
                query.setBoolean(16, stop.isDropoff());
                query.setLong(17, actor);
                query.setLong(18, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    public void shiftSchedules(Connection connection, long id, long delta, long actor) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE trip_stops
                SET scheduled_arrival_at = scheduled_arrival_at + (? * INTERVAL '1 second'),
                    scheduled_departure_at = scheduled_departure_at + (? * INTERVAL '1 second'), updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE trip_id = ?
                """
            )
        ) {
            query.setLong(1, delta);
            query.setLong(2, delta);
            query.setLong(3, actor);
            query.setLong(4, id);
            query.executeUpdate();
        }
    }
}
