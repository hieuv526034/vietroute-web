package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Guard HTTP/service ở hai context; SQL scope/quyền chéo và mutation/rollback kiểm riêng trên PostgreSQL CI. */
class DataScopeSecurityTest {

    @Test
    void scopeAdministrationStaysAdminOnlyAndValidatesInputsInTwoContexts() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var delegated = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(12L, "delegate", Set.of(), 0),
                null,
                List.of(new SimpleGrantedAuthority("ACCESS_READ"), new SimpleGrantedAuthority("ACCESS_ASSIGN"))
            );
            var admin = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0),
                null,
                List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ACCESS_READ"),
                    new SimpleGrantedAuthority("ACCESS_CREATE")
                )
            );
            String path = "/api/v1/admin/access/scopes";
            assertEquals(401, mvc.perform(get(path)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(path).with(authentication(delegated)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        put(path + "/1/members/3")
                            .with(authentication(delegated))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"roles\":[],\"active\":false,\"version\":1}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(path).with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(get(path).param("size", "101").with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        get(path + "/1/members")
                            .param("userId", "0")
                            .with(authentication(admin))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        post(path)
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(
                                "{\"code\":\"bad!\",\"nameVi\":\"A\",\"nameEn\":\"A\",\"active\":true,\"version\":0}"
                            )
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get("/api/v1/auth/scopes").with(authentication(delegated)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
        }
    }

    @Test
    void missingAuthenticationAndMismatchedServerParentScopesFailClosed() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () ->
            new DataScopeGuard().require(null, 1, "TRIP", PermissionAction.READ)
        );
        assertThrows(BusinessException.class, () -> DataScopeGuard.requireSameScope(1, 2));
        assertThrows(BusinessException.class, () -> DataScopeGuard.requireSameScope(0, 0));
        DataScopeGuard.requireSameScope(3, 3);
    }
}
