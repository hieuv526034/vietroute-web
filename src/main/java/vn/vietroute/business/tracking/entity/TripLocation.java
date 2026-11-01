package vn.vietroute.business.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột trip_locations; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "trip_locations",
    indexes = {
        @Index(name = "trip_locations_pkey", columnList = "id", unique = true),
        @Index(name = "idx_trip_locations_trip", columnList = "trip_id, id"),
        @Index(name = "idx_trip_locations_received", columnList = "received_at, id"),
        @Index(name = "idx_trip_locations_driver", columnList = "driver_user_id"),
        @Index(name = "idx_trip_locations_created_by", columnList = "created_by"),
        @Index(name = "idx_trip_locations_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "trip_locations",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "trip_locations_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "idx_trip_locations_trip", columns = { "trip_id", "id" }),
        @JdbcIndex(name = "idx_trip_locations_received", columns = { "received_at", "id" }),
        @JdbcIndex(name = "idx_trip_locations_driver", columns = { "driver_user_id" }),
        @JdbcIndex(name = "idx_trip_locations_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_trip_locations_updated_by", columns = { "updated_by" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TripLocation extends BaseEntity {

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

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy", nullable = false, precision = 9, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
