package vn.vietroute.business.trip.repo;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.trip.dto.projection.TripSegmentProjection;
import vn.vietroute.business.trip.entity.TripSegment;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của TripSegmentRepo; transaction ghi thuộc Service.
 */
public interface TripSegmentRepo extends Repository<TripSegment, Long> {
    @Query(
        value = """
        SELECT segment_order AS "segmentOrder", capacity, reserved_seats AS "reservedSeats"
        FROM {h-schema}trip_segments
        WHERE trip_id = :tripId AND segment_order >= :pickup AND segment_order < :dropoff
        ORDER BY segment_order
        FOR UPDATE
        """,
        nativeQuery = true
    )
    List<TripSegmentProjection> lockedRows(long tripId, int pickup, int dropoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE {h-schema}trip_segments
        SET reserved_seats = reserved_seats + :delta, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE trip_id = :tripId AND segment_order >= :pickup AND segment_order < :dropoff
            AND reserved_seats + :delta BETWEEN 0 AND capacity
        """,
        nativeQuery = true
    )
    int changeRows(long tripId, int pickup, int dropoff, int delta, long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    default void change(long tripId, int pickup, int dropoff, int seats, boolean reserve, long actor) {
        List<TripSegmentProjection> rows = lockedRows(tripId, pickup, dropoff);
        BusinessRules.require(
            seats > 0 && rows.size() == dropoff - pickup && !rows.isEmpty(),
            ErrorCode.BOOKING_INVALID
        );
        int order = pickup;
        for (TripSegmentProjection row : rows) {
            BusinessRules.require(row.getSegmentOrder() == order++, ErrorCode.BOOKING_INVALID);
            BusinessRules.require(
                reserve ? row.getCapacity() - row.getReservedSeats() >= seats : row.getReservedSeats() >= seats,
                ErrorCode.BOOKING_CAPACITY
            );
        }
        BusinessRules.require(
            changeRows(tripId, pickup, dropoff, reserve ? seats : -seats, actor) == rows.size(),
            ErrorCode.BOOKING_INVALID
        );
    }

    @Query(
        value = """
        SELECT part.segment_order AS "segmentOrder", SUM(b.seats) AS "seatsToRelease", COUNT(*) AS "versionChanges"
        FROM {h-schema}bookings b
        CROSS JOIN LATERAL GENERATE_SERIES(b.pickup_order, b.dropoff_order - 1) part(segment_order)
        WHERE b.trip_id = :tripId
            AND b.status = 'CONFIRMED'
        GROUP BY part.segment_order
        ORDER BY part.segment_order
        """,
        nativeQuery = true
    )
    List<TripSegmentProjection> cancellationRows(long tripId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
        SELECT segment_order AS "segmentOrder", reserved_seats AS "reservedSeats"
        FROM {h-schema}trip_segments
        WHERE trip_id = :tripId
            AND segment_order = ANY(:orderArray)
        ORDER BY segment_order
        FOR UPDATE
        """,
        nativeQuery = true
    )
    List<TripSegmentProjection> releaseRows(long tripId, @Param("orderArray") Integer[] orders);

    @Query(
        value = """
        SELECT segment_order AS "segmentOrder", capacity - reserved_seats AS "remainingSeats"
        FROM {h-schema}trip_segments
        WHERE trip_id = :tripId AND segment_order >= :pickup AND segment_order < :dropoff
        ORDER BY segment_order
        """,
        nativeQuery = true
    )
    List<TripSegmentProjection> availableRows(long tripId, int pickup, int dropoff);

    @Transactional(propagation = Propagation.MANDATORY)
    default int available(long tripId, int pickup, int dropoff) {
        List<TripSegmentProjection> rows = availableRows(tripId, pickup, dropoff);
        BusinessRules.require(rows.size() == dropoff - pickup && !rows.isEmpty(), ErrorCode.BOOKING_INVALID);
        int remaining = 200;
        int order = pickup;
        for (TripSegmentProjection row : rows) {
            BusinessRules.require(row.getSegmentOrder() == order++, ErrorCode.BOOKING_INVALID);
            remaining = Math.min(remaining, row.getRemainingSeats());
        }
        return remaining;
    }

    @Query(
        value = "SELECT reserved_seats FROM {h-schema}trip_segments WHERE trip_id = :id ORDER BY segment_order FOR UPDATE ",
        nativeQuery = true
    )
    List<Integer> reservedRows(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default int peakReserved(long id) {
        return reservedRows(id).stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
