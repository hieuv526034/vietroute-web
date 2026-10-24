package vn.vietroute.auth.entity;

import java.util.Set;

/** Projection nội bộ chỉ cho quyết định quyền; không có hash/PII và không lưu cache giữa các lần kiểm. */
public record AuthorizationState(
    long id,
    boolean active,
    boolean activationPending,
    boolean mustChangePassword,
    long tokenVersion,
    Set<Role> roles,
    Set<String> permissions
) {
    public AuthorizationState {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
