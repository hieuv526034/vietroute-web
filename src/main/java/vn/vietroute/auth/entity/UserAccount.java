package vn.vietroute.auth.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTable;

/** Tài khoản nội bộ; hash, lần đăng nhập sai và tokenVersion không được map ra API. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    schema = JdbcSchema.NAME,
    name = "auth_users",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_users_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "auth_users_username_key", columns = { "username" }, unique = true),
        @JdbcIndex(name = "idx_auth_users_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_users_updated_by", columns = { "updated_by" }),
    }
)
public class UserAccount extends BaseEntity {

    private String username;

    private String passwordHash;

    private String displayName;

    private boolean active;

    private boolean mustChangePassword;

    private int failedLoginAttempts;

    private Instant lockedUntil;

    private long tokenVersion;

    private Set<Role> roles;
}
