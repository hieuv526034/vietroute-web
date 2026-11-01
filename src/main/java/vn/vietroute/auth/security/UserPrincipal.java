package vn.vietroute.auth.security;

import java.security.Principal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

/** Principal lấy từ DB sau khi xác minh JWT, không tin vai trò do client tự gửi. */
@Getter
@Setter
public class UserPrincipal implements Principal {

    private Long id;

    private String username;

    private Set<Role> roles;

    private long tokenVersion;

    private String displayName;

    private String avatarUrl;

    public UserPrincipal(
        Long id,
        String username,
        Set<Role> roles,
        long tokenVersion,
        String displayName,
        String avatarUrl
    ) {
        roles = Set.copyOf(roles);

        this.id = id;
        this.username = username;
        this.roles = roles;
        this.tokenVersion = tokenVersion;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public Set<Role> roles() {
        return roles;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    /** Constructor ngắn cho client nội bộ/test; principal runtime dùng tên/ảnh đã đọc từ DB. */
    public UserPrincipal(Long id, String username, Set<Role> roles, long tokenVersion) {
        this(id, username, roles, tokenVersion, username, null);
    }

    @Override
    public String getName() {
        return username;
    }
}
