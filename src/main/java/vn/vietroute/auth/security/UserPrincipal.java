package vn.vietroute.auth.security;

import java.security.Principal;
import java.util.Set;
import vn.vietroute.auth.entity.Role;

/** Principal lấy từ DB sau khi xác minh JWT, không tin vai trò do client tự gửi. */
public record UserPrincipal(
    Long id,
    String username,
    Set<Role> roles,
    long tokenVersion,
    String displayName,
    String avatarUrl
) implements Principal {
    /** Constructor ngắn cho client nội bộ/test; principal runtime dùng tên/ảnh đã đọc từ DB. */
    public UserPrincipal(Long id, String username, Set<Role> roles, long tokenVersion) {
        this(id, username, roles, tokenVersion, username, null);
    }

    public UserPrincipal {
        roles = Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return username;
    }
}
