package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Kiểm API mới qua hai context WAR, không suy quyền từ việc người dùng nhìn thấy menu. SQL/quota dùng CI PostgreSQL. */
class AccessRegistrySecurityTest {

    @Test
    void registryPageRequiresCurrentReadAuthorityAndValidInheritedQuery() throws Exception {
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
            var user = new UserPrincipal(12L, "delegated", Set.of(), 0);
            var allowed = UsernamePasswordAuthenticationToken.authenticated(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ACCESS_READ"))
            );
            var denied = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
            String path = "/api/v1/admin/access/registry/roles/page";
            assertEquals(401, mvc.perform(get(path)).andReturn().getResponse().getStatus());
            assertEquals(
                403,
                mvc
                    .perform(get(path).with(authentication(denied)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(
                        get(path)
                            .param("query", "Điều hành")
                            .param("active", "false")
                            .param("size", "10")
                            .with(authentication(allowed))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (var invalid : List.of(
                new String[] { "size", "101" },
                new String[] { "page", "-1" },
                new String[] { "active", "invalid" },
                new String[] { "query", "x".repeat(161) },
                new String[] { "query", "bad\nquery" }
            )) {
                assertEquals(
                    400,
                    mvc
                        .perform(get(path).param(invalid[0], invalid[1]).with(authentication(allowed)))
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
            }
        }
    }

    @Test
    void capacitySettingsHaveFiniteValidatedBoundsAndConservativeDefaults() {
        var defaults = new AccessSettings(key -> null);
        assertEquals(512, defaults.getRegistryCapacity());
        assertEquals(256, defaults.getPolicyCapacity());
        var expanded = new AccessSettings(key -> key.equals(Env.ACCESS_REGISTRY_CAPACITY.key()) ? "600" : null);
        assertEquals(600, expanded.getRegistryCapacity());
        for (String invalid : List.of("0", "2049"))
            assertThrows(IllegalStateException.class, () ->
                new AccessSettings(key -> key.equals(Env.ACCESS_REGISTRY_CAPACITY.key()) ? invalid : null)
            );
    }
}
