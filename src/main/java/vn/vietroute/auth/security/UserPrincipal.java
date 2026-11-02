package vn.vietroute.auth.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.entity.User;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Danh tính và snapshot endpoint do server kiểm; không có hash, email hoặc dữ liệu client audit. */
@Getter
public class UserPrincipal implements Principal {

    /** Principal danh tính trước khi interceptor phê duyệt endpoint, không có grant tin từ caller. */
    public UserPrincipal(Long id, String username, Set<String> roles, long tokenVersion) {
        this(
            User.builder()
                .id(id)
                .username(username)
                .displayName(username)
                .roles(roles)
                .tokenVersion(tokenVersion)
                .build(),
            null
        );
    }

    private final Long id;
    private final String username;
    private final Set<String> roles;
    private final long tokenVersion;
    private final String displayName;
    private final Set<String> targets;
    private final Set<String> globalTargets;
    private final String target;

    public UserPrincipal(User user, String target) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.roles = Set.copyOf(user.getRoles());
        this.tokenVersion = user.getTokenVersion();
        this.displayName = user.getDisplayName();
        this.targets = Set.copyOf(user.getTargets());
        this.globalTargets = Set.copyOf(user.getGlobalTargets());
        this.target = target;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public Set<String> roles() {
        return roles;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String getName() {
        return username;
    }

    public static UserPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !(authentication.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id == null ||
            actor.id <= 0 ||
            actor.target == null
        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return actor;
    }

    public static void requireCurrent(UserPrincipal actor) {
        UserPrincipal current = current();
        if (
            actor == null ||
            !Objects.equals(actor.id, current.id) ||
            actor.tokenVersion != current.tokenVersion ||
            !actor.roles.equals(current.roles)
        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    public static void requireDelegable(Set<String> targets) {
        if (!current().targets.containsAll(targets)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    public static boolean globalData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null &&
            authentication.getPrincipal() instanceof UserPrincipal actor &&
            actor.target != null &&
            actor.globalTargets.contains(actor.target)
        );
    }
}
