package vn.vietroute.auth.security;

import java.security.Principal;
import java.util.Set;
import vn.vietroute.auth.entity.Role;

/** Principal lấy từ DB sau khi xác minh JWT, không tin vai trò do client tự gửi. */
public record UserPrincipal(Long id, String username, Set<Role> roles, long tokenVersion) implements Principal {
    public UserPrincipal { roles = Set.copyOf(roles); }
    @Override public String getName() { return username; }
}
