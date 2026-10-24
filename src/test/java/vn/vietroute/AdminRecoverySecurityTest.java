package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.response.AccessChangeStateResponse;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.AdminRecoveryRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessPermissionDefinitions;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Hai context như WAR: cứu hộ không cần quyền đã mất, nhưng không thể dùng ADMIN khác hoặc actor giả. */
class AdminRecoverySecurityTest {

    private static final String PASSWORD = "Recovery-Fixture-Only-2026!";

    @Test
    void onlyBootstrapIdentitySeesAndInvokesRecoveryEvenWithoutRoles() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, Fixtures.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var state = root.getBean(State.class);
            // Policy cũ từ DB không được khóa trang cá nhân/cứu hộ khi admin mất mọi role.
            root.getBean(AccessTestConfig.PolicyFixture.class).rules = List.of(
                new AccessPolicyResponse(
                    98,
                    "MENU",
                    "GET /account",
                    "Chặn account cũ",
                    "Legacy account deny",
                    false,
                    Set.of(),
                    Set.of(),
                    0,
                    Instant.EPOCH,
                    0
                )
            );
            var owner = auth(1, "admin", Set.of());
            String ownPage = mvc
                .perform(get("/account").with(authentication(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
            assertTrue(ownPage.contains("id=\"admin-recovery-open\""));
            // Axios đã ghép baseURL theo context; endpoint không được lặp /demo ở cả base lẫn data attribute.
            String nestedPage = mvc
                .perform(get("/demo/account").contextPath("/demo").with(authentication(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
            assertTrue(nestedPage.contains("data-endpoint=\"/api/v1/auth/admin-recovery\""));
            assertFalse(nestedPage.contains("data-endpoint=\"/demo/api/"));
            String otherPage = mvc
                .perform(get("/account").with(authentication(auth(2, "otheradmin", Set.of(Role.ADMIN)))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
            assertFalse(otherPage.contains("id=\"admin-recovery-open\""));
            String path = "/api/v1/auth/admin-recovery";
            String body = "{\"currentPassword\":\"" + PASSWORD + "\"}";
            // Không có JWT/principal thì không được dùng đường cứu hộ, kể cả đã lấy được CSRF.
            assertEquals(
                401,
                mvc
                    .perform(post(path).with(csrf()).contentType("application/json").content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(post(path).with(authentication(owner)).contentType("application/json").content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        post(path)
                            .with(authentication(auth(2, "otheradmin", Set.of(Role.ADMIN))))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            // Username trong principal giả không thay được ID/username hiện hành trong DB.
            assertEquals(
                403,
                mvc
                    .perform(
                        post(path)
                            .with(authentication(auth(2, "admin", Set.of(Role.ADMIN))))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(0, state.restores);
            var result = mvc
                .perform(
                    post(path).with(authentication(owner)).with(csrf()).contentType("application/json").content(body)
                )
                .andReturn()
                .getResponse();
            assertEquals(200, result.getStatus());
            assertTrue(result.getContentAsString().contains("\"loginRequired\":true"));
            assertFalse(result.getContentAsString().contains(PASSWORD));
            assertEquals(1, state.restores);
            assertEquals(1, state.revokes);
            var events = root.getBean(AccessTestConfig.HistoryFixture.class).events;
            assertEquals(1, events.size());
            assertEquals("ADMIN_RECOVERED", events.get(0).event().name());
        }
    }

    @Test
    void wrongPasswordCommitsFailureCountAndLocksWithoutRestoring() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, Fixtures.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            for (int attempt = 1; attempt <= 6; attempt++) {
                var result = mvc
                    .perform(
                        post("/api/v1/auth/admin-recovery")
                            .with(authentication(auth(1, "admin", Set.of())))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"currentPassword\":\"wrong\"}")
                    )
                    .andReturn()
                    .getResponse();
                assertEquals(attempt < 5 ? 400 : 429, result.getStatus());
            }
            var state = root.getBean(State.class);
            assertEquals(5, state.failures);
            assertEquals(0, state.restores);
            assertEquals(0, state.revokes);
        }
    }

    private static UsernamePasswordAuthenticationToken auth(long id, String name, Set<Role> roles) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(id, name, roles, 0),
            null,
            roles
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList()
        );
    }

    static class State {

        int failures;
        int restores;
        int revokes;
        Instant locked;
    }

    @Configuration
    static class Fixtures {

        @Bean
        State state() {
            return new State();
        }

        @Bean
        @Primary
        JdbcTransactions transactions() {
            return new JdbcTransactions(null) {
                @Override
                public <T> T execute(SqlWork<T> work) {
                    try {
                        return work.apply(null);
                    } catch (SQLException error) {
                        throw new AssertionError(error);
                    }
                }
            };
        }

        @Bean
        @Primary
        UserRepository users(State state, PasswordEncoder passwords) {
            String hash = passwords.encode(PASSWORD);
            return new UserRepository(null) {
                @Override
                public UserAccount findById(Connection connection, Long id, boolean lock) {
                    return UserAccount.builder()
                        .id(id)
                        .username(id == 1 ? "admin" : "otheradmin")
                        .active(true)
                        .passwordHash(hash)
                        .mustChangePassword(false)
                        .roles(Set.of())
                        .permissions(Set.of())
                        .failedLoginAttempts(state.failures)
                        .lockedUntil(state.locked)
                        .build();
                }

                @Override
                public void recordFailure(Connection connection, long id, int attempts, Instant locked) {
                    state.failures = attempts;
                    state.locked = locked;
                }

                @Override
                public void revokeTokens(Connection connection, long id) {
                    state.revokes++;
                }
            };
        }

        @Bean
        @Primary
        AdminRecoveryRepository recovery(State state) {
            return new AdminRecoveryRepository() {
                @Override
                public void lock(Connection connection) {}

                @Override
                public AccessChangeStateResponse state(Connection connection, long id) {
                    return new AccessChangeStateResponse(
                        state.restores == 0 ? Set.of() : Set.of("ADMIN"),
                        state.restores == 0 ? Set.of() : Set.of("ACCESS_VIEW"),
                        state.restores,
                        false,
                        true,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0
                    );
                }
            };
        }

        @Bean
        @Primary
        AccessResourceService resources(State state, AccessPermissionDefinitions definitions) {
            return new AccessResourceService(null, null, null, null, null, definitions) {
                @Override
                public void restore(Connection connection, long actor) {
                    state.restores++;
                }
            };
        }
    }
}
