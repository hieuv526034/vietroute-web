package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.response.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;
import vn.vietroute.support.NativeSecurityFixture;

/** Hai context WAR thật; quản trị tài khoản cần endpoint DB, không suy quyền từ tên role ADMIN. */
class ManagedAccountSecurityTest {

    private AnnotationConfigWebApplicationContext root;
    private AnnotationConfigWebApplicationContext child;
    private MockMvc mvc;
    private AccessTestConfig.AuthorizationFixture state;

    @BeforeEach
    void start() {
        var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
        root = new AnnotationConfigWebApplicationContext();
        root.setServletContext(web);
        root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
        root.refresh();
        child = new AnnotationConfigWebApplicationContext();
        child.setParent(root);
        child.setServletContext(web);
        child.register(WebConfig.class);
        child.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
        state = AopTestUtils.getUltimateTargetObject(root.getBean(AccessTestConfig.AuthorizationFixture.class));
        state.strict = true;
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        if (child != null) child.close();
        if (root != null) root.close();
    }

    private Authentication actor() {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(7L, "user", Set.of("ADMIN"), 2),
            null,
            Set.of()
        );
    }

    private void ready(boolean temporary, boolean verified, Set<String> targets) {
        state.states.put(
            7L,
            NativeSecurityFixture.snapshot(
                7,
                "user",
                "Người dùng",
                null,
                true,
                false,
                temporary,
                verified,
                2,
                Set.of("CUSTOM_ROLE"),
                targets,
                Set.of()
            )
        );
    }

    @Test
    void accountPageAndApiRequireIndependentEndpointGrantsInBothContexts() throws Exception {
        ready(false, true, Set.of());
        assertEquals(
            403,
            mvc
                .perform(get("/api/v1/admin/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        assertEquals(
            403,
            mvc
                .perform(get("/operations/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        ready(false, true, Set.of("GET /api/v1/admin/accounts"));
        assertEquals(
            200,
            mvc
                .perform(get("/api/v1/admin/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        assertEquals(
            403,
            mvc
                .perform(get("/operations/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        ready(false, true, Set.of("GET /api/v1/admin/accounts", "GET /operations/accounts"));
        assertEquals(
            200,
            mvc
                .perform(get("/operations/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
    }

    @Test
    void temporaryPasswordSessionHasNoPrivateAuthoritiesUntilPasswordChange() throws Exception {
        ready(true, true, Set.of("GET /api/v1/admin/accounts"));
        var blocked = mvc
            .perform(get("/api/v1/admin/accounts").with(authentication(actor())))
            .andReturn()
            .getResponse();
        assertEquals(403, blocked.getStatus());
        assertFalse(blocked.getContentAsString().contains("password_hash"));
        assertEquals(
            200,
            mvc
                .perform(get("/api/v1/auth/me").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
    }

    @Test
    void assignedAdminStillMustVerifyEmailAndCannotUsePrivateFunctions() throws Exception {
        ready(false, false, Set.of("GET /api/v1/admin/accounts"));
        assertEquals(
            403,
            mvc
                .perform(get("/api/v1/admin/accounts").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
        assertEquals(
            200,
            mvc
                .perform(get("/api/v1/auth/me").with(authentication(actor())))
                .andReturn()
                .getResponse()
                .getStatus()
        );
    }

    @Test
    void invalidRequestAndPasswordNeverReachJpaOrAppearInValidationResponses() throws Exception {
        ready(false, true, Set.of("POST /api/v1/admin/accounts"));
        var response = mvc
            .perform(
                post("/api/v1/admin/accounts")
                    .with(authentication(actor()))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        "{\"username\":\"user\",\"displayName\":\"Người dùng\",\"password\":\"private-short-secret\",\"roles\":[\"CUSTOM\"],\"createdBy\":99}"
                    )
            )
            .andReturn()
            .getResponse();
        assertEquals(400, response.getStatus());
        assertFalse(response.getContentAsString().contains("private-short-secret"));
    }
}
