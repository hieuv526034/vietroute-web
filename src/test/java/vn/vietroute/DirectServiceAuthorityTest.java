package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeSecurityFixture;

/** Actor/ủy quyền vẫn do server xác minh; bỏ action annotation không cho caller tự truyền actor mạnh hơn. */
class DirectServiceAuthorityTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void selfServiceBeansRejectAnotherUsersPrincipalBeforeReadingOrWriting() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("CUSTOM"), Set.of());
        var approved = fixture.service.authorize(fixture.actor(7, "CUSTOM", 2), "GET /api/v1/auth/me", true);
        SecurityContextHolder.getContext().setAuthentication(approved);
        for (var actor : new UserPrincipal[] {
            new UserPrincipal(8L, "other", Set.of("CUSTOM"), 2),
            new UserPrincipal(7L, "user", Set.of("ADMIN"), 2),
            new UserPrincipal(7L, "user", Set.of("CUSTOM"), 1),
        })
            assertThrows(BusinessException.class, () -> UserPrincipal.requireCurrent(actor));
    }

    @Test
    void staleRoleClaimCannotPassDelegationInsideRealService() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("CUSTOM"), Set.of("GET /one"));
        SecurityContextHolder.getContext().setAuthentication(
            fixture.service.authorize(fixture.actor(7, "ADMIN", 2), "GET /one", false)
        );
        UserPrincipal.requireDelegable(Set.of("GET /one"));
        assertThrows(BusinessException.class, () -> UserPrincipal.requireDelegable(Set.of("POST /two")));
    }
}
