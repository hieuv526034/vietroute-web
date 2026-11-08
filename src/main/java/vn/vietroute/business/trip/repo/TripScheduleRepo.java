package vn.vietroute.business.trip.repo;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.trip.entity.Trip;

/** Sinh chuyến theo tập từ lịch đã được điều hành duyệt; snapshot và chỗ độc lập theo ngày. */
@Transactional(propagation = Propagation.MANDATORY)
public interface TripScheduleRepo extends Repository<Trip, Long> {
    @Query(
        value = "SELECT 1 FROM (SELECT PG_ADVISORY_XACT_LOCK(HASHTEXT(CURRENT_SCHEMA()), 836120)) guard",
        nativeQuery = true
    )
    int lockSchedule();

    @Query(
        value = "SELECT v.id FROM {h-schema}carrier_vehicles v WHERE EXISTS (SELECT 1 FROM {h-schema}carrier_trips t WHERE t.daily_service AND t.vehicle_id = v.id) ORDER BY v.id FOR UPDATE",
        nativeQuery = true
    )
    List<Long> lockVehicles();

    @Query(
        value = "SELECT d.id FROM {h-schema}carrier_drivers d WHERE EXISTS (SELECT 1 FROM {h-schema}carrier_trips t WHERE t.daily_service AND t.driver_id = d.id) ORDER BY d.id FOR UPDATE",
        nativeQuery = true
    )
    List<Long> lockDrivers();

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1 FROM {h-schema}carrier_trips other
            CROSS JOIN GENERATE_SERIES(-1, 1) shift(day)
            WHERE other.daily_service AND other.id <> :id
                AND (other.vehicle_id = :vehicle OR other.driver_id = :driver OR other.driver_user_id = :driverUser)
                AND DATE '2000-01-02' + (CAST(:startAt AS TIMESTAMPTZ) AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME
                    < DATE '2000-01-02' + (other.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME
                        + shift.day * INTERVAL '1 day' + (other.end_at - other.departure_at)
                AND DATE '2000-01-02' + (CAST(:startAt AS TIMESTAMPTZ) AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME
                    + (CAST(:endAt AS TIMESTAMPTZ) - CAST(:startAt AS TIMESTAMPTZ))
                    > DATE '2000-01-02' + (other.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME
                        + shift.day * INTERVAL '1 day'
        )
        """,
        nativeQuery = true
    )
    boolean conflicts(long id, long vehicle, long driver, long driverUser, Instant startAt, Instant endAt);

    @Modifying
    @Query(
        value = "UPDATE {h-schema}carrier_trips SET daily_service = :enabled, version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = :actor WHERE id = :id AND version = :version",
        nativeQuery = true
    )
    int setDaily(long id, long version, boolean enabled, long actor);

    @Query(
        value = """
        WITH sources AS MATERIALIZED (
            SELECT t.* FROM {h-schema}carrier_trips t
            JOIN {h-schema}carriers c ON c.id = t.carrier_id AND c.scope_id = t.scope_id AND c.active AND c.scope_active
            JOIN {h-schema}carrier_routes r ON r.id = t.route_id AND r.active
            JOIN {h-schema}carrier_vehicles v ON v.id = t.vehicle_id AND v.active AND v.capacity >= t.capacity
            JOIN {h-schema}category vt ON vt.id = v.vehicle_type_id AND vt.active
            JOIN {h-schema}carrier_drivers d ON d.id = t.driver_id AND d.active AND d.user_id = t.driver_user_id
            JOIN {h-schema}auth_users u ON u.id = d.user_id AND u.active AND NOT u.deleted
                AND NOT u.activation_pending AND NOT u.must_change_password
            WHERE t.daily_service AND EXISTS (
                SELECT 1 FROM {h-schema}trip_events e WHERE e.trip_id = t.id AND e.event_type = 'OPENED'
            ) AND EXISTS (
                SELECT 1 FROM {h-schema}auth_user_roles ur
                JOIN {h-schema}auth_roles ar ON ar.code = ur.role_code AND ar.active AND NOT ar.deleted
                JOIN {h-schema}auth_user_roles global_role ON global_role.user_id = ur.user_id
                    AND global_role.role_code = ur.role_code AND global_role.scope_id = 0 AND global_role.active
                WHERE ur.user_id = u.id AND ur.scope_id = t.scope_id AND ur.active
            ) AND NOT EXISTS (
                SELECT 1 FROM {h-schema}trip_stops st
                JOIN {h-schema}category l ON l.id = st.location_id
                JOIN {h-schema}category w ON w.id = l.parent_id
                JOIN {h-schema}category p ON p.id = st.province_id
                WHERE st.trip_id = t.id AND (NOT l.active OR NOT w.active OR NOT p.active)
            )
        ), candidates AS MATERIALIZED (
            SELECT s.*, (day.at::DATE + (s.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME)
                AT TIME ZONE 'Asia/Ho_Chi_Minh' AS starts
            FROM sources s
            CROSS JOIN GENERATE_SERIES(
                CAST(:fromAt AS TIMESTAMPTZ) AT TIME ZONE 'Asia/Ho_Chi_Minh',
                (CAST(:toAt AS TIMESTAMPTZ) AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '1 day',
                INTERVAL '1 day'
            ) day(at)
        ), inserted AS (
            INSERT INTO {h-schema}carrier_trips(carrier_id, scope_id, route_id, vehicle_id, driver_id, driver_user_id,
                code, name_vi, name_en, plate_number, vehicle_type_id, vehicle_type_name_vi, vehicle_type_name_en,
                driver_name, driver_phone, departure_at, end_at, capacity, status, service_source_id, created_by, updated_by)
            SELECT s.carrier_id, s.scope_id, s.route_id, s.vehicle_id, s.driver_id, s.driver_user_id,
                'D' || s.id || 'T' || TO_CHAR(s.starts AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDD'),
                s.name_vi, s.name_en, s.plate_number, s.vehicle_type_id, s.vehicle_type_name_vi, s.vehicle_type_name_en,
                s.driver_name, s.driver_phone, s.starts, s.starts + (s.end_at - s.departure_at), s.capacity, 'OPEN', s.id, 0, 0
            FROM candidates s
            WHERE s.starts > CURRENT_TIMESTAMP AND s.starts > s.departure_at
                AND NOT EXISTS (
                    SELECT 1 FROM {h-schema}carrier_trips existing
                    WHERE (existing.route_id = s.route_id AND existing.departure_at = s.starts
                        OR existing.status IN ('OPEN', 'IN_PROGRESS'))
                        AND (existing.vehicle_id = s.vehicle_id OR existing.driver_id = s.driver_id
                            OR existing.driver_user_id = s.driver_user_id)
                        AND existing.departure_at < s.starts + (s.end_at - s.departure_at) AND existing.end_at > s.starts
                )
            ON CONFLICT DO NOTHING
            RETURNING *
        ), stops AS (
            INSERT INTO {h-schema}trip_stops(trip_id, carrier_id, scope_id, location_id, province_id, stop_order,
                name_vi, name_en, address, latitude, longitude, scheduled_arrival_at, scheduled_departure_at,
                cumulative_fare, pickup_allowed, dropoff_allowed, created_by, updated_by)
            SELECT i.id, i.carrier_id, i.scope_id, st.location_id, st.province_id, st.stop_order,
                st.name_vi, st.name_en, st.address, st.latitude, st.longitude,
                i.departure_at + (st.scheduled_arrival_at - s.departure_at),
                i.departure_at + (st.scheduled_departure_at - s.departure_at),
                st.cumulative_fare, st.pickup_allowed, st.dropoff_allowed, 0, 0
            FROM inserted i JOIN sources s ON s.id = i.service_source_id
            JOIN {h-schema}trip_stops st ON st.trip_id = s.id
            RETURNING id
        ), segments AS (
            INSERT INTO {h-schema}trip_segments(trip_id, carrier_id, scope_id, segment_order, capacity, updated_by)
            SELECT i.id, i.carrier_id, i.scope_id, seg.segment_order, i.capacity, 0
            FROM inserted i JOIN {h-schema}trip_segments seg ON seg.trip_id = i.service_source_id
            RETURNING trip_id
        ), events AS (
            INSERT INTO {h-schema}trip_events(trip_id, carrier_id, scope_id, event_type, note, created_by)
            SELECT i.id, i.carrier_id, i.scope_id, event.kind, 'Lịch chạy hằng ngày', 0
            FROM inserted i CROSS JOIN (VALUES ('CREATED'), ('OPENED')) event(kind)
            RETURNING id
        ) SELECT COUNT(*) FROM inserted
        """,
        nativeQuery = true
    )
    long materialize(Instant fromAt, Instant toAt);
}
