package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_roles; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_roles")
@JdbcTable(name = "auth_roles", primaryKey = { "code" })
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @Column(name = "code", length = 24)
    private String code;

    @Column(name = "name_vi", length = 120)
    private String nameVi;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(name = "active")
    private boolean active;

    @Column(name = "deleted")
    private boolean deleted;

    @Column(name = "functions_initialized")
    private boolean functionsInitialized;

    @Column(name = "all_scopes")
    private boolean allScopes;

    @Column(name = "protected_role")
    private boolean protectedRole;

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
