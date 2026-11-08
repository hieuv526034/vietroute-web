package vn.vietroute.business.trip.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.trip.dto.projection.JourneyProjection;
import vn.vietroute.business.trip.dto.projection.TripSegmentProjection;
import vn.vietroute.business.trip.dto.projection.TripStopProjection;
import vn.vietroute.business.trip.dto.response.JourneyMatchResponse;
import vn.vietroute.business.trip.dto.response.JourneyResponse;
import vn.vietroute.business.trip.dto.response.JourneySegmentResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.business.trip.dto.search.JourneySearch;
import vn.vietroute.business.trip.entity.Trip;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của JourneyRepo; transaction ghi thuộc Service.
 */
public interface JourneyRepo extends Repository<Trip, Long> {
    String FILTER = """
    FROM {h-schema}carrier_trips t
    JOIN {h-schema}carriers c ON c.id = t.carrier_id AND c.scope_id = t.scope_id
    JOIN (
        SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
            scope_active AS active, scope_version AS version, updated_at, updated_by
        FROM {h-schema}carriers
    ) ds ON ds.id = t.scope_id
    JOIN {h-schema}trip_stops pick ON pick.trip_id = t.id AND pick.pickup_allowed
        AND pick.actual_departure_at IS NULL
    JOIN {h-schema}trip_stops dropoff ON dropoff.trip_id = t.id AND dropoff.dropoff_allowed
        AND dropoff.stop_order > pick.stop_order
    JOIN LATERAL (
        SELECT MIN(capacity - reserved_seats) AS remaining, COUNT(*) AS segments
        FROM {h-schema}trip_segments seg
        WHERE seg.trip_id = t.id AND seg.segment_order >= pick.stop_order
            AND seg.segment_order < dropoff.stop_order
    ) avail ON avail.segments = dropoff.stop_order - pick.stop_order
    WHERE c.active AND ds.active
        AND EXISTS (
        SELECT 1
        FROM {h-schema}trip_events published
        WHERE published.trip_id = t.id
            AND published.event_type = 'OPENED'
    )
        AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
        AND pick.scheduled_departure_at >= :fromAt
        AND pick.scheduled_departure_at < :toAt
        AND (CAST(:fromTime AS TIME) IS NULL OR (pick.scheduled_departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME >= :fromTime)
        AND (CAST(:toTime AS TIME) IS NULL OR (pick.scheduled_departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME <= :toTime)
        AND ((CAST(:fromLocationId AS BIGINT) IS NOT NULL AND pick.location_id = :fromLocationId) OR
        (CAST(:fromLocationId AS BIGINT) IS NULL AND (CAST(:fromProvinceId AS BIGINT) IS NULL OR pick.province_id = :fromProvinceId)))
        AND ((CAST(:toLocationId AS BIGINT) IS NOT NULL AND dropoff.location_id = :toLocationId) OR
            (CAST(:toLocationId AS BIGINT) IS NULL AND
            (CAST(:toProvinceId AS BIGINT) IS NULL OR dropoff.province_id = :toProvinceId)))
        AND (CAST(:carrierId AS BIGINT) IS NULL OR c.id = :carrierId)
        AND (CAST(:vehicleTypeId AS BIGINT) IS NULL OR t.vehicle_type_id = :vehicleTypeId)
        AND avail.remaining >= :seats
        AND (CAST(:minPrice AS NUMERIC) IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare >= :minPrice)
        AND (CAST(:maxPrice AS NUMERIC) IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare <= :maxPrice)
        AND (CAST(:fromWardId AS BIGINT) IS NULL OR EXISTS (
            SELECT 1 FROM {h-schema}category location WHERE location.id=pick.location_id AND
                location.parent_id=:fromWardId
    ))
        AND (CAST(:toWardId AS BIGINT) IS NULL OR EXISTS (
            SELECT 1 FROM {h-schema}category location WHERE location.id=dropoff.location_id AND
                location.parent_id=:toWardId
    ))
    """;

    @Query(value = "SELECT COUNT(*) " + FILTER, nativeQuery = true)
    long searchCount(
        Instant fromAt,
        Instant toAt,
        LocalTime fromTime,
        LocalTime toTime,
        Long fromLocationId,
        Long fromProvinceId,
        Long toLocationId,
        Long toProvinceId,
        Long carrierId,
        Long vehicleTypeId,
        int seats,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long fromWardId,
        Long toWardId
    );

    @Query(
        value = """
        SELECT t.id, t.code, c.id AS "carrierId", c.name AS "carrierName",
            t.vehicle_type_name_vi AS "vehicleTypeNameVi", t.vehicle_type_name_en AS "vehicleTypeNameEn",
            pick.id AS "pickupStopId", dropoff.id AS "dropoffStopId", pick.name_vi AS "pickupNameVi",
            pick.name_en AS "pickupNameEn", dropoff.name_vi AS "dropoffNameVi",
            dropoff.name_en AS "dropoffNameEn", pick.scheduled_departure_at AS "pickupAt",
            dropoff.scheduled_arrival_at AS "dropoffAt",
            dropoff.cumulative_fare - pick.cumulative_fare AS "unitPrice",
            avail.remaining AS "availableSeats", t.status
        """ +
            FILTER +
            """
            ORDER BY pick.scheduled_departure_at, t.id, pick.stop_order, dropoff.stop_order
            LIMIT :pageSize OFFSET :offset
            """,
        nativeQuery = true
    )
    List<JourneyProjection> searchRows(
        Instant fromAt,
        Instant toAt,
        LocalTime fromTime,
        LocalTime toTime,
        Long fromLocationId,
        Long fromProvinceId,
        Long toLocationId,
        Long toProvinceId,
        Long carrierId,
        Long vehicleTypeId,
        int seats,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long fromWardId,
        Long toWardId,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<JourneyMatchResponse> search(JourneySearch body) {
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        LocalDate date = body.getDate() == null ? LocalDate.now(zone) : body.getDate();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date
            .plusDays(body.getDate() == null ? 31 : 1)
            .atStartOfDay(zone)
            .toInstant();
        long total = searchCount(
            from,
            to,
            body.getFromTime(),
            body.getToTime(),
            body.getFromLocationId(),
            body.getFromProvinceId(),
            body.getToLocationId(),
            body.getToProvinceId(),
            body.getCarrierId(),
            body.getVehicleTypeId(),
            body.getSeats(),
            body.getMinPrice(),
            body.getMaxPrice(),
            body.getFromWardId(),
            body.getToWardId()
        );
        List<JourneyMatchResponse> rows = searchRows(
            from,
            to,
            body.getFromTime(),
            body.getToTime(),
            body.getFromLocationId(),
            body.getFromProvinceId(),
            body.getToLocationId(),
            body.getToProvinceId(),
            body.getCarrierId(),
            body.getVehicleTypeId(),
            body.getSeats(),
            body.getMinPrice(),
            body.getMaxPrice(),
            body.getFromWardId(),
            body.getToWardId(),
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(row -> {
                return new JourneyMatchResponse(
                    row.getId(),
                    row.getCode(),
                    row.getCarrierId(),
                    row.getCarrierName(),
                    row.getVehicleTypeNameVi(),
                    row.getVehicleTypeNameEn(),
                    row.getPickupStopId(),
                    row.getDropoffStopId(),
                    row.getPickupNameVi(),
                    row.getPickupNameEn(),
                    row.getDropoffNameVi(),
                    row.getDropoffNameEn(),
                    row.getPickupAt(),
                    row.getDropoffAt(),
                    row.getUnitPrice(),
                    row.getAvailableSeats(),
                    row.getStatus()
                );
            })
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = """
        SELECT t.id, t.code, t.carrier_id AS "carrierId", c.name AS "carrierName",
            c.phone AS "carrierPhone", t.name_vi AS "nameVi", t.name_en AS "nameEn",
            t.vehicle_type_name_vi AS "vehicleTypeNameVi", t.vehicle_type_name_en AS "vehicleTypeNameEn",
            t.plate_number AS "plateNumber", t.departure_at AS "departureAt", t.end_at AS "endAt",
            t.status, t.operation_note AS "operationNote"
        FROM {h-schema}carrier_trips t
        JOIN {h-schema}carriers c ON c.id = t.carrier_id AND c.scope_id = t.scope_id
        JOIN (
            SELECT scope_id AS id, scope_code AS code, scope_name_vi AS name_vi, scope_name_en AS name_en,
                scope_active AS active, scope_version AS version, updated_at, updated_by
            FROM {h-schema}carriers
        ) ds ON ds.id = t.scope_id
            AND ds.active
        WHERE t.id = :tripId
            AND t.status <> 'DRAFT'
            AND EXISTS (
            SELECT 1
            FROM {h-schema}trip_events published
            WHERE published.trip_id = t.id
                AND published.event_type = 'OPENED'
        )
        """,
        nativeQuery = true
    )
    List<JourneyProjection> detailRows(long tripId);

    @Query(
        value = """
        SELECT segment_order AS "segmentOrder", capacity - reserved_seats AS "remainingSeats"
        FROM {h-schema}trip_segments
        WHERE trip_id = :tripId
        ORDER BY segment_order
        """,
        nativeQuery = true
    )
    List<TripSegmentProjection> segmentRows(long tripId);

    @Query(
        value = """
        SELECT id, location_id AS "locationId", province_id AS "provinceId", stop_order AS "stopOrder",
            name_vi AS "nameVi", name_en AS "nameEn", address, latitude,
            longitude, scheduled_arrival_at AS "scheduledArrivalAt",
            scheduled_departure_at AS "scheduledDepartureAt", actual_arrival_at AS "actualArrivalAt",
            actual_departure_at AS "actualDepartureAt", cumulative_fare AS "cumulativeFare",
            pickup_allowed AS "pickupAllowed", dropoff_allowed AS "dropoffAllowed"
        FROM {h-schema}trip_stops
        WHERE trip_id = :tripId
        ORDER BY stop_order
        """,
        nativeQuery = true
    )
    List<TripStopProjection> stopRows(long tripId);

    @Transactional(readOnly = true)
    default JourneyResponse find(long id) {
        List<JourneyProjection> rows = detailRows(id);
        BusinessRules.require(!rows.isEmpty(), ErrorCode.NOT_FOUND);
        JourneyProjection row = rows.get(0);
        List<JourneySegmentResponse> segments = segmentRows(id)
            .stream()
            .map(part -> new JourneySegmentResponse(part.getSegmentOrder(), part.getRemainingSeats()))
            .toList();
        return new JourneyResponse(
            row.getId(),
            row.getCode(),
            row.getCarrierId(),
            row.getCarrierName(),
            row.getCarrierPhone(),
            row.getNameVi(),
            row.getNameEn(),
            row.getVehicleTypeNameVi(),
            row.getVehicleTypeNameEn(),
            row.getPlateNumber(),
            row.getDepartureAt(),
            row.getEndAt(),
            row.getStatus(),
            row.getOperationNote(),
            stopRows(id).stream().map(TripStopResponse::from).toList(),
            segments
        );
    }
}
