package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Tài khoản nội bộ; hash, lần đăng nhập sai và tokenVersion không được map ra API. */
@Entity
@Table(name = "auth_users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "auth_users",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_users_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "auth_users_username_key", columns = { "username" }, unique = true),
        @JdbcIndex(name = "uq_auth_users_email", columns = { "email" }, unique = true),
        @JdbcIndex(name = "idx_auth_users_avatar", columns = { "avatar_media_id" }),
        @JdbcIndex(name = "idx_auth_users_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_users_updated_by", columns = { "updated_by" }),
    }
)
public class User extends BaseEntity {

    @Column(name = "username", length = 32, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @Transient
    private String avatarUrl;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "activation_pending")
    private boolean activationPending;

    @Column(name = "active")
    private boolean active;

    @Column(name = "must_change_password")
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "token_version")
    private long tokenVersion;

    @Builder.Default
    @Transient
    private Set<String> roles = Set.of();

    @Builder.Default
    @Transient
    private Set<String> targets = Set.of();

    @Builder.Default
    @Transient
    private Set<String> globalTargets = Set.of();

    @Column(name = "previous_password_hash_1", length = 100)
    private String previousPasswordHash1;

    @Column(name = "previous_password_hash_2", length = 100)
    private String previousPasswordHash2;

    @Column(name = "bootstrap_scope_pending")
    private boolean bootstrapScopePending;

    @Column(name = "deleted")
    private boolean deleted;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_membership_versions", columnDefinition = "jsonb")
    private Map<String, Long> scopeMembershipVersions = new LinkedHashMap<>();
}
