package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Dựng hai context như WAR: nhập URL trực tiếp và menu phải cùng chính sách; ADMIN không bỏ guard nền. */
class AccessPolicyTest {

    @Test
    void dynamicCodesPreserveJsonAndCannotForgeRoleAuthorities() throws Exception {
        var json = new ObjectMapper();
        assertEquals("\"SUPPORT\"", json.writeValueAsString(Role.valueOf("SUPPORT")));
        assertEquals(Permission.valueOf("ORDER_ASSIGN"), json.readValue("\"ORDER_ASSIGN\"", Permission.class));
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> Permission.valueOf("ROLE_ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> Permission.valueOf("ROLE_READ"));
        assertThrows(IllegalArgumentException.class, () -> Permission.valueOf("ORDER_WRITE"));
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("bad-code"));
    }

    @Test
    void menuPolicyBlocksDirectPageAndEndpointAndKeepsMandatoryGuards() throws Exception {
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
            var rules = root.getBean(AccessTestConfig.PolicyFixture.class);
            rules.rules = List.of(
                new AccessPolicyResponse(
                    1,
                    "MENU",
                    "GET /media",
                    "Ảnh",
                    "Images",
                    true,
                    Set.of("CUSTOM_READ"),
                    Set.of(),
                    0,
                    Instant.EPOCH,
                    0
                )
            );
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var user = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(8L, "support", Set.of(Role.valueOf("SUPPORT")), 0),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPPORT"), new SimpleGrantedAuthority("MEDIA_READ"))
            );
            assertEquals(
                403,
                mvc
                    .perform(get("/media").with(authentication(user)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            String home = mvc
                .perform(get("/").with(authentication(user)))
                .andReturn()
                .getResponse()
                .getContentAsString();
            assertFalse(home.contains("href=\"/media?lang="));
            rules.rules = List.of(
                new AccessPolicyResponse(
                    2,
                    "ENDPOINT",
                    "GET /media",
                    "Ảnh",
                    "Images",
                    true,
                    Set.of(),
                    Set.of("SUPPORT"),
                    0,
                    Instant.EPOCH,
                    0
                )
            );
            assertEquals(
                200,
                mvc
                    .perform(get("/media").with(authentication(user)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var missingBase = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(9L, "other", Set.of(Role.valueOf("SUPPORT")), 0),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPPORT"))
            );
            assertEquals(
                403,
                mvc
                    .perform(get("/media").with(authentication(missingBase)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            rules.rules = List.of(
                new AccessPolicyResponse(
                    3,
                    "ENDPOINT",
                    "GET /media",
                    "Ảnh",
                    "Images",
                    false,
                    Set.of(),
                    Set.of(),
                    0,
                    Instant.EPOCH,
                    0
                )
            );
            assertEquals(
                403,
                mvc
                    .perform(get("/media").with(authentication(user)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var admin = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MEDIA_READ"))
            );
            assertEquals(
                200,
                mvc
                    .perform(get("/media").with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get("/api/v1/admin/access/registry/roles").with(authentication(user)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var assignOnly = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ACCESS_ASSIGN"))
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        post("/api/v1/admin/access/registry/roles/SUPPORT")
                            .with(authentication(assignOnly))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"nameVi\":\"Hỗ trợ\",\"nameEn\":\"Support\",\"active\":true,\"version\":0}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            rules.rules = List.of();
            assertEquals(
                200,
                mvc
                    .perform(get("/media").with(authentication(user)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertTrue(root.getBean(HikariDataSource.class).getHikariPoolMXBean() == null);
        }
    }
}
