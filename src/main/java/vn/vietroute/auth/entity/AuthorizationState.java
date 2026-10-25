package vn.vietroute.auth.entity;

import java.util.Set;

/** Projection nội bộ chỉ cho quyết định quyền; không có hash/PII và không lưu cache giữa các lần kiểm. */
public record AuthorizationState(
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
    public AuthorizationState {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        resourceTargets = Set.copyOf(resourceTargets);
        legacyPermissions = Set.copyOf(legacyPermissions);
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
