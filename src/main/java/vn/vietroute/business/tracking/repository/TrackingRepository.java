package vn.vietroute.business.tracking.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.tracking.dto.request.TrackingSampleRequest;
import vn.vietroute.business.tracking.dto.response.TrackingPointResponse;
import vn.vietroute.business.tracking.dto.response.TrackingResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.common.config.integration.TrackingSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Vị trí ghi thẳng DB; caller luôn giữ guard và khóa trip, không đọc metadata consent qua API public. */
@Repository
public class TrackingRepository {

    public record State(
        UUID key,
        boolean sharing,
        long driverUserId,
        Instant startedAt,
        TrackingPointResponse latest
    ) {}

    /** Caller đã khóa trip; không khóa ngược state → trip. */
    public State state(Connection connection, long tripId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM trip_tracking WHERE trip_id=?")) {
            query.setLong(1, tripId);
            try (var row = query.executeQuery()) {
                return row.next()
                    ? new State(
                          row.getObject("session_key", UUID.class),
                          row.getBoolean("sharing"),
                          row.getLong("driver_user_id"),
                          row.getTimestamp("started_at").toInstant(),
                          point(row)
                      )
                    : null;
            }
        }
    }

    /** Bật lại luôn cấp nonce mới, xóa snapshot cũ; không tái công bố mẫu của lần consent hoặc tài xế trước. */
    public void start(Connection connection, TripResponse trip, UUID key, Instant now, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO trip_tracking(trip_id,carrier_id,scope_id,driver_user_id,session_key,sharing,started_at,created_by,updated_by)
                VALUES(?,?,?,?,?,true,?,?,?) ON CONFLICT(trip_id) DO UPDATE SET
                driver_user_id=EXCLUDED.driver_user_id,session_key=EXCLUDED.session_key,sharing=true,started_at=EXCLUDED.started_at,
                latitude=NULL,longitude=NULL,accuracy=NULL,measured_at=NULL,received_at=NULL,
                updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP,version=trip_tracking.version+1
                """
            )
        ) {
            query.setLong(1, trip.id());
            query.setLong(2, trip.carrierId());
            query.setLong(3, trip.scopeId());
            query.setLong(4, actor);
            query.setObject(5, key);
            query.setObject(6, now.atOffset(ZoneOffset.UTC));
            query.setLong(7, actor);
            query.setLong(8, actor);
            query.executeUpdate();
        }
    }

    /** Dừng/đóng chuyến xóa tọa độ snapshot trong cùng transaction; history chỉ còn phục vụ retention nội bộ. */
    public void stop(Connection connection, long tripId, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_tracking SET sharing=false,latitude=NULL,longitude=NULL,accuracy=NULL,measured_at=NULL,received_at=NULL,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=? AND sharing"
            )
        ) {
            query.setLong(1, actor);
            query.setLong(2, tripId);
            query.executeUpdate();
        }
    }

    /** Snapshot và mẫu lịch sử cùng transaction, tần suất/nonce đã được service kiểm dưới khóa trip. */
    public void sample(Connection connection, TripResponse trip, TrackingSampleRequest body, Instant now, long actor)
        throws SQLException {
        prune(connection, trip.id(), now);
        try (
            var query = connection.prepareStatement(
                "UPDATE trip_tracking SET latitude=?,longitude=?,accuracy=?,measured_at=?,received_at=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE trip_id=? AND session_key=? AND sharing AND driver_user_id=?"
            )
        ) {
            query.setBigDecimal(1, body.latitude());
            query.setBigDecimal(2, body.longitude());
            query.setBigDecimal(3, body.accuracy());
            query.setObject(4, body.measuredAt().atOffset(ZoneOffset.UTC));
            query.setObject(5, now.atOffset(ZoneOffset.UTC));
            query.setLong(6, actor);
            query.setLong(7, trip.id());
            query.setObject(8, body.sessionKey());
            query.setLong(9, actor);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.TRACKING_SESSION);
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO trip_locations(trip_id,carrier_id,scope_id,driver_user_id,session_key,source,latitude,longitude,accuracy,measured_at,received_at,created_by,updated_by) VALUES(?,?,?,?,?,'BROWSER',?,?,?,?,?,?,?)"
            )
        ) {
            query.setLong(1, trip.id());
            query.setLong(2, trip.carrierId());
            query.setLong(3, trip.scopeId());
            query.setLong(4, actor);
            query.setObject(5, body.sessionKey());
            query.setBigDecimal(6, body.latitude());
            query.setBigDecimal(7, body.longitude());
            query.setBigDecimal(8, body.accuracy());
            query.setObject(9, body.measuredAt().atOffset(ZoneOffset.UTC));
            query.setObject(10, now.atOffset(ZoneOffset.UTC));
            query.setLong(11, actor);
            query.setLong(12, actor);
            query.executeUpdate();
        }
    }

    /** Khóa SHARE trên trip giữ snapshot/trace nhất quán với bật/tắt/cập nhật, không cho GET hồi sinh consent. */
    public TrackingResponse view(
        Connection connection,
        long tripId,
        String resource,
        Long bookingId,
        Long customerId,
        boolean enabled,
        Instant now
    ) throws SQLException {
        String sql =
            "SELECT t.status,t.driver_user_id AS assigned_user," +
            (bookingId == null
                ? "true"
                : "EXISTS(SELECT 1 FROM bookings b WHERE b.id=? AND b.trip_id=t.id AND b.customer_id=? AND b.status='CONFIRMED')") +
            " AS eligible FROM carrier_trips t JOIN carriers owner ON owner.id=t.carrier_id AND owner.scope_id=t.scope_id JOIN auth_data_scopes ds ON ds.id=t.scope_id AND ds.active WHERE t.id=?" +
            (resource == null ? "" : " AND " + DataScopeGuard.visibleSql()) +
            " FOR SHARE OF t";
        String status;
        long assigned;
        boolean eligible;
        try (var query = connection.prepareStatement(sql)) {
            int at = 1;
            if (bookingId != null) {
                query.setLong(at++, bookingId);
                query.setLong(at++, customerId);
            }
            query.setLong(at++, tripId);
            if (resource != null) DataScopeGuard.bindVisible(query, at, resource, PermissionAction.READ);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                status = row.getString("status");
                assigned = row.getLong("assigned_user");
                eligible = row.getBoolean("eligible");
            }
        }
        if (!enabled) return empty("DISABLED", now, null);
        if (status.equals("COMPLETED") || status.equals("CANCELLED") || status.equals("DRAFT")) return empty(
            "CLOSED",
            now,
            null
        );
        if (!eligible) return empty("UNAVAILABLE", now, null);
        var state = state(connection, tripId);
        if (state == null || !state.sharing() || state.driverUserId() != assigned) return empty("OFF", now, null);
        Instant latest = state.latest() == null ? state.startedAt() : state.latest().receivedAt();
        if (now.isAfter(latest.plusSeconds(TrackingSettings.LEASE_SECONDS))) return empty(
            "STALE",
            now,
            state.latest() == null ? null : latest
        );
        if (state.latest() == null) return empty("WAITING", now, null);
        var points = new ArrayList<TrackingPointResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT latitude,longitude,accuracy,measured_at,received_at FROM trip_locations WHERE trip_id=? AND session_key=? AND received_at>=? ORDER BY id DESC LIMIT 100"
            )
        ) {
            query.setLong(1, tripId);
            query.setObject(2, state.key());
            query.setObject(3, now.minusSeconds(TrackingSettings.RETENTION_DAYS * 86400L).atOffset(ZoneOffset.UTC));
            try (var row = query.executeQuery()) {
                while (row.next()) points.add(point(row));
            }
        }
        return new TrackingResponse(
            now.isAfter(latest.plusSeconds(TrackingSettings.STALE_SECONDS)) ? "STALE" : "LIVE",
            "UNCONFIGURED",
            now,
            latest,
            state.latest(),
            List.copyOf(points)
        );
    }

    private static TrackingResponse empty(String status, Instant now, Instant lastReceived) {
        return new TrackingResponse(status, "UNCONFIGURED", now, lastReceived, null, List.of());
    }

    private static TrackingPointResponse point(ResultSet row) throws SQLException {
        var measured = row.getTimestamp("measured_at");
        return measured == null
            ? null
            : new TrackingPointResponse(
                  row.getBigDecimal("latitude"),
                  row.getBigDecimal("longitude"),
                  row.getBigDecimal("accuracy"),
                  measured.toInstant(),
                  row.getTimestamp("received_at").toInstant()
              );
    }

    /** Cắt trace theo trip trước INSERT; cleanup chung theo lô có advisory lock, không chờ node khác hoặc giữ hàng đợi RAM. */
    private void prune(Connection connection, long tripId, Instant now) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "DELETE FROM trip_locations WHERE id IN(SELECT id FROM trip_locations WHERE trip_id=? ORDER BY id DESC OFFSET ? LIMIT ?)"
            )
        ) {
            query.setLong(1, tripId);
            query.setInt(2, TrackingSettings.TRIP_CAPACITY - 1);
            query.setInt(3, TrackingSettings.CLEANUP_BATCH);
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(781205622)");
            var row = query.executeQuery()
        ) {
            row.next();
            if (!row.getBoolean(1)) return;
        }
        try (
            var query = connection.prepareStatement(
                "DELETE FROM trip_locations WHERE id IN(SELECT id FROM trip_locations WHERE received_at<? ORDER BY received_at,id LIMIT ?)"
            )
        ) {
            query.setObject(1, now.minusSeconds(TrackingSettings.RETENTION_DAYS * 86400L).atOffset(ZoneOffset.UTC));
            query.setInt(2, TrackingSettings.CLEANUP_BATCH);
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "DELETE FROM trip_locations WHERE id IN(SELECT id FROM trip_locations ORDER BY received_at,id LIMIT LEAST(?,GREATEST(0,(SELECT count(*) FROM trip_locations)-?)))"
            )
        ) {
            query.setInt(1, TrackingSettings.CLEANUP_BATCH);
            query.setInt(2, TrackingSettings.GLOBAL_CAPACITY - 1);
            query.executeUpdate();
        }
    }
}
