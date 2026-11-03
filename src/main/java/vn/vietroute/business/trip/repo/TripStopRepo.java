package vn.vietroute.business.trip.repo;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.entity.TripStop;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Actual timestamp do server ghi trên cùng transaction sau khi service đã khóa trip/kiểm phân công. */
public interface TripStopRepo extends Repository<TripStop, Long> {
    @Transactional
    @Modifying
    @Query(
        value = """
        UPDATE {h-schema}trip_stops
        SET actual_arrival_at = :now, updated_at = CURRENT_TIMESTAMP, updated_by = :actor,
            version = version + 1
        WHERE trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId
            AND stop_order = :stopOrder AND actual_arrival_at IS NULL
        """,
        nativeQuery = true
    )
    int markArrival(
        @Param("tripId") long tripId,
        @Param("carrierId") long carrierId,
        @Param("scopeId") long scopeId,
        @Param("stopOrder") int stopOrder,
        @Param("now") Instant now,
        @Param("actor") long actor
    );

    @Transactional
    @Modifying
    @Query(
        value = """
        UPDATE {h-schema}trip_stops
        SET actual_departure_at = :now, updated_at = CURRENT_TIMESTAMP, updated_by = :actor,
            version = version + 1
        WHERE trip_id = :tripId AND carrier_id = :carrierId AND scope_id = :scopeId
            AND stop_order = :stopOrder AND actual_departure_at IS NULL
        """,
        nativeQuery = true
    )
    int markDeparture(
        @Param("tripId") long tripId,
        @Param("carrierId") long carrierId,
        @Param("scopeId") long scopeId,
        @Param("stopOrder") int stopOrder,
        @Param("now") Instant now,
        @Param("actor") long actor
    );

    /** Giữ CAS một dòng; lệnh JPA tham gia transaction JPA/JDBC đang mở của service. */
    default void mark(TripResponse trip, int order, boolean arrival, Instant now, long actor) {
        int changed = arrival
            ? markArrival(trip.getId(), trip.getCarrierId(), trip.getScopeId(), order, now, actor)
            : markDeparture(trip.getId(), trip.getCarrierId(), trip.getScopeId(), order, now, actor);
        if (changed != 1) throw new BusinessException(ErrorCode.TRIP_STATE_CONFLICT);
    }
}
