package vn.vietroute.business.journey.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.journey.dto.response.JourneyMatchResponse;
import vn.vietroute.business.journey.dto.response.JourneyResponse;
import vn.vietroute.business.journey.dto.response.JourneySegmentResponse;
import vn.vietroute.business.journey.dto.search.JourneySearch;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Chỉ công bố chuyến đã từng OPEN; projection public bỏ account/driver PII và dữ liệu đặt chỗ. */
@Repository
public class JourneyRepo extends BaseRepository {

    private final TripRepo trips;
    private static final String PUBLISHED = """
    EXISTS (
        SELECT 1
        FROM trip_events published
        WHERE published.trip_id = t.id
            AND published.event_type = 'OPENED'
    )
    """;

    public JourneyRepo(JdbcConnectionFactory connections, TripRepo trips) {
        super(connections);
        this.trips = trips;
    }

    /** Ngày tại pickup, hướng tăng, pickup chưa rời và MIN chỗ của đúng range chặng trong SQL. */
    public PageResponse<JourneyMatchResponse> search(JourneySearch body) {
        return read(connection -> {
            String filter = """
            FROM carrier_trips t
            JOIN carriers c ON c.id = t.carrier_id AND c.scope_id = t.scope_id
            JOIN (
                SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                    scope_active AS active, scope_version AS version, updated_at, updated_by
                FROM carriers
            ) ds ON ds.id = t.scope_id
            JOIN trip_stops pick ON pick.trip_id = t.id AND pick.pickup_allowed
                AND pick.actual_departure_at IS NULL
            JOIN trip_stops dropoff ON dropoff.trip_id = t.id AND dropoff.dropoff_allowed
                AND dropoff.stop_order > pick.stop_order
            JOIN LATERAL (
                SELECT min(capacity - reserved_seats) AS remaining, count(*) AS segments
                FROM trip_segments seg
                WHERE seg.trip_id = t.id AND seg.segment_order >= pick.stop_order
                    AND seg.segment_order < dropoff.stop_order
            ) avail ON avail.segments = dropoff.stop_order - pick.stop_order
            WHERE c.active AND ds.active
                AND %s
                AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
                AND pick.scheduled_departure_at >= ?
                AND pick.scheduled_departure_at < ?
                AND ((?::bigint IS NOT NULL AND pick.location_id = ?) OR (?::bigint IS NULL AND pick.province_id = ?))
                AND ((?::bigint IS NOT NULL AND dropoff.location_id = ?) OR (?::bigint IS NULL AND
                    dropoff.province_id = ?))
                AND (?::bigint IS NULL OR c.id = ?)
                AND (?::bigint IS NULL OR t.vehicle_type_id = ?)
                AND avail.remaining >= ?
                AND (?::numeric IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare >= ?)
                AND (?::numeric IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare <= ?)
            """.formatted(PUBLISHED);
            long total;
            try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + filter)) {
                bind(query, body);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            ArrayList<JourneyMatchResponse> result = new ArrayList<JourneyMatchResponse>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT t.id, t.code, c.id, c.name, t.vehicle_type_name_vi, t.vehicle_type_name_en, pick.id,
                        dropoff.id, pick.name_vi, pick.name_en, dropoff.name_vi, dropoff.name_en,
                            pick.scheduled_departure_at,
                        dropoff.scheduled_arrival_at, dropoff.cumulative_fare - pick.cumulative_fare, avail.remaining,
                        t.status
                    %s
                    ORDER BY pick.scheduled_departure_at, t.id, pick.stop_order, dropoff.stop_order
                    LIMIT ? OFFSET ?
                    """.formatted(filter)
                )
            ) {
                int at = bind(query, body);
                query.setInt(at++, body.getSize());
                query.setLong(at, body.offset());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next())
                        result.add(
                            new JourneyMatchResponse(
                                row.getLong(1),
                                row.getString(2),
                                row.getLong(3),
                                row.getString(4),
                                row.getString(5),
                                row.getString(6),
                                row.getLong(7),
                                row.getLong(8),
                                row.getString(9),
                                row.getString(10),
                                row.getString(11),
                                row.getString(12),
                                row.getTimestamp(13).toInstant(),
                                row.getTimestamp(14).toInstant(),
                                row.getBigDecimal(15),
                                row.getInt(16),
                                row.getString(17)
                            )
                        );
                }
            }
            return PageResponse.of(result, body, total);
        });
    }

    private int bind(PreparedStatement query, JourneySearch body) throws SQLException {
        int at = 1;
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        query.setTimestamp(at++, Timestamp.from(body.getDate().atStartOfDay(zone).toInstant()));
        query.setTimestamp(at++, Timestamp.from(body.getDate().plusDays(1).atStartOfDay(zone).toInstant()));
        for (int i = 0; i < 3; i++) query.setObject(at++, body.getFromLocationId());
        query.setObject(at++, body.getFromProvinceId());
        for (int i = 0; i < 3; i++) query.setObject(at++, body.getToLocationId());
        query.setObject(at++, body.getToProvinceId());
        query.setObject(at++, body.getCarrierId());
        query.setObject(at++, body.getCarrierId());
        query.setObject(at++, body.getVehicleTypeId());
        query.setObject(at++, body.getVehicleTypeId());
        query.setInt(at++, body.getSeats());
        query.setBigDecimal(at++, body.getMinPrice());
        query.setBigDecimal(at++, body.getMinPrice());
        query.setBigDecimal(at++, body.getMaxPrice());
        query.setBigDecimal(at++, body.getMaxPrice());
        return at;
    }

    /** Sau công bố vẫn xem được thông báo hủy/hoàn thành; DRAFT hoặc chưa từng mở không xuất hiện. */
    public JourneyResponse find(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT t.id, t.code, t.carrier_id, c.name, c.phone, t.name_vi, t.name_en, t.vehicle_type_name_vi,
                    t.vehicle_type_name_en, t.plate_number, t.departure_at, t.end_at, t.status, t.operation_note
                FROM carrier_trips t
                JOIN carriers c ON c.id = t.carrier_id AND c.scope_id = t.scope_id
                JOIN (
                    SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                        scope_active AS active, scope_version AS version, updated_at, updated_by
                    FROM carriers
                ) ds ON ds.id = t.scope_id
                    AND ds.active
                WHERE t.id = ?
                    AND t.status <> 'DRAFT'
                    AND %s
                """.formatted(PUBLISHED)
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                ArrayList<JourneySegmentResponse> segments = new ArrayList<JourneySegmentResponse>();
                try (
                    PreparedStatement sub = connection.prepareStatement(
                        """
                        SELECT segment_order, capacity - reserved_seats
                        FROM trip_segments
                        WHERE trip_id = ?
                        ORDER BY segment_order
                        """
                    )
                ) {
                    sub.setLong(1, id);
                    try (ResultSet part = sub.executeQuery()) {
                        while (part.next()) segments.add(new JourneySegmentResponse(part.getInt(1), part.getInt(2)));
                    }
                }
                return new JourneyResponse(
                    row.getLong(1),
                    row.getString(2),
                    row.getLong(3),
                    row.getString(4),
                    row.getString(5),
                    row.getString(6),
                    row.getString(7),
                    row.getString(8),
                    row.getString(9),
                    row.getString(10),
                    row.getTimestamp(11).toInstant(),
                    row.getTimestamp(12).toInstant(),
                    row.getString(13),
                    row.getString(14),
                    trips.stops(connection, id),
                    segments
                );
            }
        }
    }
}
