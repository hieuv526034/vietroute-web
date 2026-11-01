package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeSecurityFixture;
import vn.vietroute.support.TestJpa;

/** Snapshot DB độc lập token/authority; kiểm engine endpoint mới, không tái tạo engine action đã bỏ. */
class EndpointSecurityTest {

    private static final String LIST = "GET /api/v1/native-items";

    private static final String UPDATE = "PUT /api/v1/native-items/{id}";

    private final StateRepo states = new StateRepo();

    private final EndpointAccess service = new EndpointAccess(null, states, null);

    private Authentication login() {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(7L, "user", Set.of("ADMIN"), 2),
            null,
            Set.of(new SimpleGrantedAuthority(UPDATE), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private User snapshot(
        boolean active,
        boolean pending,
        boolean temporary,
        boolean verified,
        long version,
        Set<String> targets
    ) {
        return NativeSecurityFixture.snapshot(
            7,
            "user",
            "Người dùng",
            null,
            active,
            pending,
            temporary,
            verified,
            version,
            Set.of("VAITRO_TUYCHON"),
            targets,
            Set.of(LIST)
        );
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customRoleUsesOnlyExactDbEndpointAndIgnoresJwtAdminAndForgedAuthorities() {
        states.value = snapshot(true, false, false, true, 2, Set.of(LIST));
        var approved = service.authorize(login(), LIST, false);
        assertEquals(Set.of("VAITRO_TUYCHON"), ((UserPrincipal) approved.getPrincipal()).roles());
        assertEquals(
            Set.of(LIST),
            approved.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet())
        );
        assertEquals(
            ErrorCode.ACCESS_DENIED,
            assertThrows(BusinessException.class, () -> service.authorize(login(), UPDATE, false)).getErrorCode()
        );
        assertThrows(BusinessException.class, () -> service.authorize(login(), "GET /api/v1/native-items/{id}", false));
        assertThrows(BusinessException.class, () -> service.authorize(login(), "DELETE /api/v1/native-items", false));
    }

    @Test
    void revocationAndDbFailuresNeverFallBackToPreviousSnapshot() {
        states.value = snapshot(true, false, false, true, 2, Set.of(LIST));
        var previous = service.authorize(login(), LIST, false);
        states.value = snapshot(true, false, false, true, 2, Set.of());
        assertThrows(BusinessException.class, () -> service.authorize(previous, LIST, false));
        states.failure = true;
        assertEquals(
            ErrorCode.DATABASE_UNAVAILABLE,
            assertThrows(BusinessException.class, () -> service.authorize(previous, LIST, false)).getErrorCode()
        );
    }

    @Test
    void disabledDeletedPendingAndTokenRevocationInvalidateTheIdentity() {
        for (var state : new User[] {
            null,
            snapshot(false, false, false, true, 2, Set.of(LIST)),
            snapshot(true, true, false, true, 2, Set.of(LIST)),
            snapshot(true, false, false, true, 3, Set.of(LIST)),
        }) {
            states.value = state;
            assertEquals(
                ErrorCode.TOKEN_INVALID,
                assertThrows(BusinessException.class, () -> service.authorize(login(), LIST, false)).getErrorCode()
            );
        }
    }

    @Test
    void temporaryAndUnverifiedAccountsCanCompleteSetupButHaveNoPrivateAuthority() {
        states.value = snapshot(true, false, true, true, 2, Set.of(LIST));
        assertEquals(
            ErrorCode.PASSWORD_CHANGE_REQUIRED,
            assertThrows(BusinessException.class, () -> service.authorize(login(), LIST, false)).getErrorCode()
        );
        assertTrue(service.authorize(login(), "POST /api/v1/auth/password", true).getAuthorities().isEmpty());
        states.value = snapshot(true, false, false, false, 2, Set.of(LIST));
        assertEquals(
            ErrorCode.EMAIL_VERIFICATION_REQUIRED,
            assertThrows(BusinessException.class, () -> service.authorize(login(), LIST, false)).getErrorCode()
        );
        assertTrue(service.authorize(login(), "GET /api/v1/auth/me", true).getAuthorities().isEmpty());
    }

    @Test
    void serverActorAndDelegationRequireAnApprovedContextAndAnEndpointSubset() {
        var forged = new UserPrincipal(7L, "user", Set.of("ADMIN"), 2);
        SecurityContextHolder.getContext().setAuthentication(login());
        assertThrows(BusinessException.class, () -> UserPrincipal.requireCurrent(forged));
        states.value = snapshot(true, false, false, true, 2, Set.of(LIST));
        var approved = service.authorize(login(), LIST, false);
        SecurityContextHolder.getContext().setAuthentication(approved);
        var actor = (UserPrincipal) approved.getPrincipal();
        UserPrincipal.requireCurrent(actor);
        UserPrincipal.requireDelegable(Set.of(LIST));
        assertThrows(BusinessException.class, () -> UserPrincipal.requireDelegable(Set.of(LIST, UPDATE)));
        assertThrows(BusinessException.class, () -> UserPrincipal.requireCurrent(forged));
        assertTrue(UserPrincipal.globalData());
        states.value = snapshot(true, false, false, true, 2, Set.of(LIST, UPDATE));
        SecurityContextHolder.getContext().setAuthentication(service.authorize(login(), UPDATE, false));
        assertFalse(UserPrincipal.globalData());
    }

    private static class StateRepo extends UserRepo {

        private User value;
        private boolean failure;

        StateRepo() {
            super(TestJpa.MANAGER, null);
        }

        @Override
        public User load(Long id) {
            if (failure) throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
            return value;
        }
    }
}
