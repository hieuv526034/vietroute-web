package vn.vietroute.auth.entity;

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
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_user_roles; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_user_roles")
@JdbcTable(name = "auth_user_roles", primaryKey = { "user_id", "role_code", "scope_id" })
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_code", length = 24)
    private String roleCode;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "active")
    private boolean active;

    @Version
    @Column(name = "version")
    private long version;

    @Column(name = "global_scope_id", insertable = false, updatable = false)
    private Long globalScopeId;

    @Column(name = "carrier_scope_id", insertable = false, updatable = false)
    private Long carrierScopeId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "updated_by")
    private long updatedBy;
}
