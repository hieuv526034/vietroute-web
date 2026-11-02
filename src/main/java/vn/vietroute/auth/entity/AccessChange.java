package vn.vietroute.auth.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_access_changes; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_access_changes")
@JdbcTable(name = "auth_access_changes", primaryKey = { "id" })
@Getter
@Setter
@NoArgsConstructor
public class AccessChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "target_kind", length = 20)
    private String targetKind;

    @Column(name = "target_key", length = 240)
    private String targetKey;

    @Column(name = "actor_id")
    private long actorId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private JsonNode beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private JsonNode afterState;

    @Version
    @Column(name = "version")
    private long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "updated_by")
    private long updatedBy;
}
