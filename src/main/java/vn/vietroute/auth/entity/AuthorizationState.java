package vn.vietroute.auth.entity;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Projection nội bộ chỉ cho quyết định quyền; không có hash/PII và không lưu cache giữa các lần kiểm. */
@Getter
@Setter
public class AuthorizationState {

    private long id;

    private boolean active;

    private boolean activationPending;

    private boolean mustChangePassword;

    private boolean emailVerified;

    private long tokenVersion;

    private Set<Role> roles;

    private Set<String> permissions;

    private Set<String> resourceTargets;

    private Set<String> legacyPermissions;

    public AuthorizationState(
        long id,
        boolean active,
        boolean activationPending,
        boolean mustChangePassword,
        boolean emailVerified,
        long tokenVersion,
        Set<Role> roles,
        Set<String> permissions,
        Set<String> resourceTargets,
        Set<String> legacyPermissions
    ) {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        resourceTargets = Set.copyOf(resourceTargets);
        legacyPermissions = Set.copyOf(legacyPermissions);

        this.id = id;
        this.active = active;
        this.activationPending = activationPending;
        this.mustChangePassword = mustChangePassword;
        this.emailVerified = emailVerified;
        this.tokenVersion = tokenVersion;
        this.roles = roles;
        this.permissions = permissions;
        this.resourceTargets = resourceTargets;
        this.legacyPermissions = legacyPermissions;
    }

    public long id() {
        return id;
    }

    public boolean active() {
        return active;
    }

    public boolean activationPending() {
        return activationPending;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public Set<String> resourceTargets() {
        return resourceTargets;
    }

    public Set<String> legacyPermissions() {
        return legacyPermissions;
    }

    /** Snapshot tương thích nội bộ cho role chưa chuyển đổi; không nhận giá trị này từ request/JWT. */
    public AuthorizationState(
        long id,
        boolean active,
        boolean activationPending,
        boolean mustChangePassword,
        boolean emailVerified,
        long tokenVersion,
        Set<Role> roles,
        Set<String> permissions
    ) {
        this(
            id,
            active,
            activationPending,
            mustChangePassword,
            emailVerified,
            tokenVersion,
            roles,
            permissions,
            Set.of(),
            permissions
        );
    }

    /** Cùng điều kiện cho mọi role: tài khoản gốc được lifecycle cấp sẵn trạng thái này trong DB. */
    public boolean ready() {
        return !mustChangePassword && emailVerified;
    }
}
