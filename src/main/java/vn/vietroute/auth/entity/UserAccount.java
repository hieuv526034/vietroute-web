package vn.vietroute.auth.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;

/** Tài khoản nội bộ; hash, lần đăng nhập sai và tokenVersion không được map ra API. */
@Getter @Setter @SuperBuilder @NoArgsConstructor
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
