package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** DB state độc lập authorities của Authentication: chứng minh annotation gọi bean/DAO thật, không mock cho qua guard. */
class DatabaseAccessGuardTest {

    private final AccessTestConfig.AuthorizationFixture db = new AccessTestConfig.AuthorizationFixture();
    private final DatabaseAccessGuard guard = new DatabaseAccessGuard(db);

    @Test
    void authorityTextAndAdminRoleClaimsCannotCreatePermissionsWithoutDbMapping() {
        db.strict = true;
        db.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
        var forged = actor("ROLE_ADMIN", "CATALOG_DELETE");
        assertTrue(guard.has(forged, "CATALOG_READ"));
        assertFalse(guard.has(forged, "CATALOG_DELETE"));
        assertFalse(guard.admin(forged));
        assertFalse(guard.adminAll(forged, "CATALOG_READ"));
        db.states.put(8L, state(true, false, false, 0, Set.of(Role.ADMIN), Set.of()));
        assertTrue(guard.admin(forged));
        assertFalse(guard.adminAll(forged, "CATALOG_READ"));
        assertFalse(guard.any(forged));
        assertFalse(guard.has(forged, "ROLE_ADMIN"));
    }

    @Test
    void revocationAccountStateAndTokenVersionAreReadAgainWithoutCaching() {
        db.strict = true;
        var auth = actor("CATALOG_READ");
        db.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
        assertTrue(guard.has(auth, "CATALOG_READ"));
        db.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of()));
        assertFalse(guard.has(auth, "CATALOG_READ"));
        for (var changed : List.of(
            state(false, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")),
            state(true, true, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")),
            state(true, false, false, 1, Set.of(Role.DRIVER), Set.of("CATALOG_READ"))
        )) {
            db.states.put(8L, changed);
            assertFalse(guard.authenticated(auth));
            assertFalse(guard.has(auth, "CATALOG_READ"));
        }
        db.states.put(8L, state(true, false, true, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
        assertTrue(guard.authenticated(auth));
        assertFalse(guard.has(auth, "CATALOG_READ"));
        assertTrue(guard.refresh(auth).getAuthorities().isEmpty());
        db.states.clear();
        assertFalse(guard.has(auth, "CATALOG_READ"));
        int reads = db.reads;
        assertFalse(guard.has(null, "CATALOG_READ"));
        assertFalse(
            guard.has(UsernamePasswordAuthenticationToken.authenticated("invalid", null, List.of()), "CATALOG_READ")
        );
        assertEquals(reads, db.reads);
    }

    @Test
    void anyAllAndCustomCodesUseOneDbSnapshotPerCheck() {
        db.strict = true;
        db.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("BOOKING_LIST", "CATALOG_CREATE")));
        int before = db.reads;
        assertTrue(guard.all(actor(), "BOOKING_LIST", "CATALOG_CREATE"));
        assertEquals(before + 1, db.reads);
        assertTrue(guard.any(actor(), "CATALOG_DELETE", "BOOKING_LIST"));
        assertFalse(guard.all(actor(), "BOOKING_LIST", "CATALOG_DELETE"));
        assertFalse(guard.has(actor(), "CATALOG_WRITE"));
    }

    @Test
    void rootAndServletExpressionsQueryDbAndFailClosedOnOutage() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, ProbeConfig.class);
            root.refresh();
            servlet.setServletContext(web);
            servlet.setParent(root);
            servlet.register(WebConfig.class, ProbeController.class);
            servlet.refresh();
            var fixture = root.getBean(AccessTestConfig.AuthorizationFixture.class);
            fixture.strict = true;
            fixture.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
            var auth = actor("ROLE_ADMIN", "CATALOG_DELETE");
            var probe = root.getBean(GuardedProbe.class);
            SecurityContextHolder.getContext().setAuthentication(auth);
            assertTrue(probe.read());
            assertThrows(AccessDeniedException.class, probe::delete);
            fixture.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of()));
            assertThrows(AccessDeniedException.class, probe::read);
            SecurityContextHolder.clearContext();
            fixture.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
            var mvc = MockMvcBuilders.webAppContextSetup(servlet).apply(springSecurity()).build();
            assertEquals(
                200,
                mvc
                    .perform(get("/api/v1/guard-probe").with(authentication(auth)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            fixture.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of()));
            assertEquals(
                403,
                mvc
                    .perform(get("/api/v1/guard-probe").with(authentication(auth)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            // Interceptor đọc lần 1 thành công, DB lỗi đúng lúc PreAuthorize đọc lần 2: controller không chạy.
            fixture.states.put(8L, state(true, false, false, 0, Set.of(Role.DRIVER), Set.of("CATALOG_READ")));
            fixture.reads = 0;
            fixture.failAt = 2;
            var failure = mvc
                .perform(get("/api/v1/guard-probe").with(authentication(auth)))
                .andReturn()
                .getResponse();
            assertEquals(503, failure.getStatus());
            assertTrue(failure.getContentAsString().contains("COM007"));
            assertEquals(1, servlet.getBean(ProbeController.class).count());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static AuthorizationState state(
        boolean active,
        boolean pending,
        boolean temporary,
        long version,
        Set<Role> roles,
        Set<String> permissions
    ) {
        return new AuthorizationState(8, active, pending, temporary, version, roles, permissions);
    }

    private static UsernamePasswordAuthenticationToken actor(String... codes) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(8L, "unit", Set.of(Role.ADMIN), 0),
            null,
            List.of(codes).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    @Configuration
    static class ProbeConfig {

        @Bean
        GuardedProbe guardedProbe() {
            return new GuardedProbe();
        }
    }

    static class GuardedProbe {

        @PreAuthorize(AccessRules.CATALOG_READ)
        public boolean read() {
            return true;
        }

        @PreAuthorize(AccessRules.CATALOG_DELETE)
        public boolean delete() {
            return true;
        }
    }

    @RestController
    @AccessFunction(group = Groups.CATALOG, function = Functions.CATALOG, permissions = { Permissions.CATALOG_READ })
    static class ProbeController {

        final AtomicInteger calls = new AtomicInteger();

        public int count() {
            return calls.get();
        }

        @GetMapping("/api/v1/guard-probe")
        @Operation(summary = "Fixture kiểm guard DB")
        @PreAuthorize(AccessRules.CATALOG_READ)
        public String read() {
            calls.incrementAndGet();
            return "allowed";
        }
    }
}
