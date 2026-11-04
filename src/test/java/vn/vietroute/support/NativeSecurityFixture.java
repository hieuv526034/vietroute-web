package vn.vietroute.support;

import java.time.Instant;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.auth.security.UserPrincipal;

public class NativeSecurityFixture {

    public User state;
    public int reads;
    public final UserRepo repository = TestJpa.stub(UserRepo.class, (method, args) -> {
        if (method.getName().equals("load") && args.length == 1) return load((Long) args[0]);
        throw new AssertionError(method);
    });

    public final EndpointAccess service = new EndpointAccess(null, repository, null);

    public Authentication actor(long id, String role, long version) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(id, "user", Set.of(role), version),
            null,
            Set.of()
        );
    }

    public User ready(Set<String> roles, Set<String> targets) {
        return snapshot(7, "user", "Người dùng", null, true, false, false, true, 2, roles, targets, Set.of());
    }

    public static User snapshot(
        long id,
        String username,
        String name,
        String avatar,
        boolean active,
        boolean pending,
        boolean temporary,
        boolean verified,
        long version,
        Set<String> roles,
        Set<String> targets
    ) {
        return snapshot(
            id,
            username,
            name,
            avatar,
            active,
            pending,
            temporary,
            verified,
            version,
            roles,
            targets,
            Set.of()
        );
    }

    public static User snapshot(
        long id,
        String username,
        String name,
        String avatar,
        boolean active,
        boolean pending,
        boolean temporary,
        boolean verified,
        long version,
        Set<String> roles,
        Set<String> targets,
        Set<String> globals
    ) {
        return User.builder()
            .id(id)
            .username(username)
            .displayName(name)
            .active(active)
            .activationPending(pending)
            .mustChangePassword(temporary)
            .emailVerifiedAt(verified ? Instant.now() : null)
            .tokenVersion(version)
            .roles(roles)
            .targets(targets)
            .globalTargets(globals)
            .build();
    }

    public User load(Long id) {
        reads++;
        return state;
    }
}
