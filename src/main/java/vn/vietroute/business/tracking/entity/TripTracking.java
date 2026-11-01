package vn.vietroute.business.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột trip_tracking; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_tracking",
    indexes = {
        @Index(name = "trip_tracking_pkey", columnList = "trip_id", unique = true),
        @Index(name = "idx_trip_tracking_driver", columnList = "driver_user_id"),
        @Index(name = "idx_trip_tracking_created_by", columnList = "created_by"),
        @Index(name = "idx_trip_tracking_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "trip_tracking",
    primaryKey = { "trip_id" },
    indexes = {
        @JdbcIndex(name = "trip_tracking_pkey", columns = { "trip_id" }, unique = true),
        @JdbcIndex(name = "idx_trip_tracking_driver", columns = { "driver_user_id" }),
        @JdbcIndex(name = "idx_trip_tracking_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trip_tracking_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TripTracking {

    @Id
    @Column(name = "trip_id", nullable = false)
    private long tripId;

    @Column(name = "carrier_id", nullable = false)
    private long carrierId;

    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    @Column(name = "driver_user_id", nullable = false)
    private long driverUserId;

    @Column(name = "session_key", nullable = false)
    private UUID sessionKey;

    @Column(name = "sharing", nullable = false)
    private boolean sharing;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy", precision = 9, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "measured_at")
    private Instant measuredAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(name = "updated_by", nullable = false)
    private long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
