package vn.vietroute.business.trip.repo;

import static vn.vietroute.common.base.BaseRepository.decimalValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.trip.dto.response.JourneyMatchResponse;
import vn.vietroute.business.trip.dto.response.JourneyResponse;
import vn.vietroute.business.trip.dto.response.JourneySegmentResponse;
import vn.vietroute.business.trip.dto.search.JourneySearch;
import vn.vietroute.business.trip.repo.TripRepo;
import vn.vietroute.common.base.BaseRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Chỉ công bố chuyến đã từng OPEN; projection public bỏ account/driver PII và dữ liệu đặt chỗ. */
@Repository
public class JourneyRepo extends BaseRepository {

    private final TripRepo trips;
    private static final String PUBLISHED = """
    EXISTS (
        SELECT 1
        FROM {h-schema}trip_events published
        WHERE published.trip_id = t.id
            AND published.event_type = 'OPENED'
    )
    """;

    public JourneyRepo(EntityManager manager, TripRepo trips) {
        super(manager);
        this.trips = trips;
    }

    /** Ngày tại pickup, hướng tăng, pickup chưa rời và MIN chỗ của đúng range chặng trong SQL. */
    @Transactional(readOnly = true)
    public PageResponse<JourneyMatchResponse> search(JourneySearch body) {
        String filter = """
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
            AND %s
            AND (t.status = 'IN_PROGRESS' OR (t.status = 'OPEN' AND t.departure_at > CURRENT_TIMESTAMP))
            AND pick.scheduled_departure_at >= :fromAt
            AND pick.scheduled_departure_at < :toAt
            AND ((CAST(:fromLocationId AS BIGINT) IS NOT NULL AND pick.location_id = :fromLocationId) OR (CAST(:fromLocationId AS BIGINT) IS NULL AND pick.province_id = :fromProvinceId))
            AND ((CAST(:toLocationId AS BIGINT) IS NOT NULL AND dropoff.location_id = :toLocationId) OR (CAST(:toLocationId AS BIGINT) IS NULL AND
                dropoff.province_id = :toProvinceId))
            AND (CAST(:carrierId AS BIGINT) IS NULL OR c.id = :carrierId)
            AND (CAST(:vehicleTypeId AS BIGINT) IS NULL OR t.vehicle_type_id = :vehicleTypeId)
            AND avail.remaining >= :seats
            AND (CAST(:minPrice AS NUMERIC) IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare >= :minPrice)
            AND (CAST(:maxPrice AS NUMERIC) IS NULL OR dropoff.cumulative_fare - pick.cumulative_fare <= :maxPrice)
            AND (CAST(:fromWardId AS BIGINT) IS NULL OR EXISTS (
                SELECT 1 FROM {h-schema}category location WHERE location.id=pick.location_id AND location.parent_id=:fromWardId
            ))
            AND (CAST(:toWardId AS BIGINT) IS NULL OR EXISTS (
                SELECT 1 FROM {h-schema}category location WHERE location.id=dropoff.location_id AND location.parent_id=:toWardId
            ))
        """.formatted(PUBLISHED);
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            bind(query, body);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<JourneyMatchResponse> result = new ArrayList<JourneyMatchResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT t.id, t.code, c.id, c.name, t.vehicle_type_name_vi, t.vehicle_type_name_en, pick.id,
                    dropoff.id, pick.name_vi, pick.name_en, dropoff.name_vi, dropoff.name_en,
                        pick.scheduled_departure_at,
                    dropoff.scheduled_arrival_at, dropoff.cumulative_fare - pick.cumulative_fare, avail.remaining,
                    t.status
                %s
                ORDER BY pick.scheduled_departure_at, t.id, pick.stop_order, dropoff.stop_order
                LIMIT :pageSize OFFSET :offset
                """.formatted(filter),
                Tuple.class
            );

            bind(query, body);
            query.setParameter("pageSize", body.getSize());
            query.setParameter("offset", body.offset());
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    result.add(
                        new JourneyMatchResponse(
                            longValue(row.get(0)),
                            stringValue(row.get(1)),
                            longValue(row.get(2)),
                            stringValue(row.get(3)),
                            stringValue(row.get(4)),
                            stringValue(row.get(5)),
                            longValue(row.get(6)),
                            longValue(row.get(7)),
                            stringValue(row.get(8)),
                            stringValue(row.get(9)),
                            stringValue(row.get(10)),
                            stringValue(row.get(11)),
                            instantValue(row.get(12)),
                            instantValue(row.get(13)),
                            decimalValue(row.get(14)),
                            intValue(row.get(15)),
                            stringValue(row.get(16))
                        )
                    );
                }
            }
        }
        return PageResponse.of(result, body, total);
    }

    private void bind(Query query, JourneySearch body) {
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        query.setParameter("fromAt", body.getDate().atStartOfDay(zone).toInstant());
        query.setParameter("toAt", body.getDate().plusDays(1).atStartOfDay(zone).toInstant());
        query.setParameter("fromLocationId", body.getFromLocationId());
        query.setParameter("fromProvinceId", body.getFromProvinceId());
        query.setParameter("toLocationId", body.getToLocationId());
        query.setParameter("toProvinceId", body.getToProvinceId());
        query.setParameter("carrierId", body.getCarrierId());
        query.setParameter("vehicleTypeId", body.getVehicleTypeId());
        query.setParameter("seats", body.getSeats());
        query.setParameter("minPrice", body.getMinPrice());
        query.setParameter("maxPrice", body.getMaxPrice());
        query.setParameter("fromWardId", body.getFromWardId());
        query.setParameter("toWardId", body.getToWardId());
    }

    /** Sau công bố vẫn xem được thông báo hủy/hoàn thành; DRAFT hoặc chưa từng mở không xuất hiện. */
    public JourneyResponse find(long id) {
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT t.id, t.code, t.carrier_id, c.name, c.phone, t.name_vi, t.name_en, t.vehicle_type_name_vi,
                    t.vehicle_type_name_en, t.plate_number, t.departure_at, t.end_at, t.status, t.operation_note
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
                    AND %s
                """.formatted(PUBLISHED),
                Tuple.class
            );

            query.setParameter("tripId", id);
            {
                List<?> rowValues = query.getResultList();

                if (rowValues.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
                Tuple row = (Tuple) rowValues.get(0);

                ArrayList<JourneySegmentResponse> segments = new ArrayList<JourneySegmentResponse>();
                {
                    Query sub = entityManager.createNativeQuery(
                        """
                        SELECT segment_order, capacity - reserved_seats
                        FROM {h-schema}trip_segments
                        WHERE trip_id = :tripId
                        ORDER BY segment_order
                        """,
                        Tuple.class
                    );

                    sub.setParameter("tripId", id);
                    {
                        for (Object partValue : sub.getResultList()) {
                            Tuple part = (Tuple) partValue;
                            segments.add(new JourneySegmentResponse(intValue(part.get(0)), intValue(part.get(1))));
                        }
                    }
                }
                return new JourneyResponse(
                    longValue(row.get(0)),
                    stringValue(row.get(1)),
                    longValue(row.get(2)),
                    stringValue(row.get(3)),
                    stringValue(row.get(4)),
                    stringValue(row.get(5)),
                    stringValue(row.get(6)),
                    stringValue(row.get(7)),
                    stringValue(row.get(8)),
                    stringValue(row.get(9)),
                    instantValue(row.get(10)),
                    instantValue(row.get(11)),
                    stringValue(row.get(12)),
                    stringValue(row.get(13)),
                    trips.stops(id),
                    segments
                );
            }
        }
    }
}
