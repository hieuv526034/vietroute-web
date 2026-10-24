package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.AccessRegistryRequest;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Hai context như WAR: kiểm URL trực tiếp, principal hiển thị và binding trước khi chạm DB. */
class AccessProfileSecurityTest {

    @Test
    void adminRegistryCannotBeRenamedDisabledOrDeleted() {
        var service = new AccessManagementService(null, null, null, null);
        var actor = new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0);
        for (boolean active : List.of(true, false)) {
            var failure = assertThrows(BusinessException.class, () ->
                service.save("roles", "ADMIN", new AccessRegistryRequest("Đổi tên", "Changed", active, 0), actor, false)
            );
            assertEquals(ErrorCode.ACCESS_DENIED, failure.getErrorCode());
        }
        assertEquals(
            ErrorCode.ACCESS_DENIED,
            assertThrows(BusinessException.class, () -> service.deleteRole("ADMIN", 0, actor)).getErrorCode()
        );
    }

    @Test
    void customDbRoleUsesActionGuardAndAuthenticatedPagesKeepIdentity() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, ProfileUsers.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var spec = root
                .getBean(ObjectMapper.class)
                .readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
            assertTrue(spec.path("paths").path("/api/v1/auth/avatar").path("post").path("responses").has("201"));
            String jwt = root.getBean(JwtService.class).issue(fixtureUser()).accessToken();
            String docs = mvc
                .perform(get("/docs").param("lang", "vi").cookie(new Cookie(Http.AUTH_COOKIE, jwt)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
            assertTrue(docs.contains("Người quản lý"));
            assertFalse(docs.contains("href=\"/login?lang=vi\""));
            assertEquals(
                200,
                mvc
                    .perform(get("/docs").cookie(new Cookie(Http.AUTH_COOKIE, "expired-invalid-cookie")))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var actor = new UserPrincipal(
                8L,
                "editor",
                Set.of(Role.valueOf("CATALOG_EDITOR")),
                0,
                "Người quản lý",
                "https://res.cloudinary.com/demo/image/upload/avatar.png"
            );
            var allowed = UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(
                    new SimpleGrantedAuthority("ROLE_CATALOG_EDITOR"),
                    new SimpleGrantedAuthority("CATALOG_READ"),
                    new SimpleGrantedAuthority("CATALOG_VIEW")
                )
            );
            var denied = UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CATALOG_EDITOR"))
            );
            assertEquals(
                200,
                mvc
                    .perform(get("/admin").with(authentication(allowed)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get("/admin").with(authentication(denied)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            String home = mvc
                .perform(get("/").param("lang", "vi").with(authentication(allowed)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
            assertTrue(home.contains("Người quản lý"));
            assertTrue(home.contains("https://res.cloudinary.com/demo/image/upload/avatar.png"));
            assertFalse(home.contains("href=\"/login?lang=vi\""));
            assertFalse(home.contains("id=\"registry-dialog\""));
            assertEquals(
                "/account?lang=vi",
                mvc
                    .perform(get("/account-access").param("lang", "vi").with(authentication(allowed)))
                    .andReturn()
                    .getResponse()
                    .getRedirectedUrl()
            );
            assertEquals(
                401,
                mvc
                    .perform(
                        put("/api/v1/auth/profile")
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"displayName\":\"A\",\"avatarMediaId\":null,\"version\":0}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        put("/api/v1/auth/profile")
                            .with(authentication(allowed))
                            .contentType("application/json")
                            .content("{\"displayName\":\"A\",\"version\":0}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (String payload : List.of(
                "{\"displayName\":\"A\",\"version\":0,\"roles\":[\"ADMIN\"]}",
                "{\"displayName\":\"A\"}",
                "{\"displayName\":\"\",\"version\":0}"
            )) {
                assertEquals(
                    400,
                    mvc
                        .perform(
                            put("/api/v1/auth/profile")
                                .with(authentication(allowed))
                                .with(csrf())
                                .contentType("application/json")
                                .content(payload)
                        )
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
            }
        }
    }

    /** Chỉ test filter đọc danh tính; không mở connection/đọc .env hoặc bỏ guard của controller. */
    @Configuration
    static class ProfileUsers {

        @Bean
        @Primary
        UserRepository profileUsers() {
            return new UserRepository(null) {
                @Override
                public UserAccount findById(Long id) {
                    return fixtureUser();
                }
            };
        }
    }

    private static UserAccount fixtureUser() {
        return UserAccount.builder()
            .id(8L)
            .username("editor")
            .displayName("Người quản lý")
            .active(true)
            .roles(Set.of(Role.valueOf("CATALOG_EDITOR")))
            .permissions(Set.of(Permission.CATALOG_READ))
            .createdAt(Instant.EPOCH)
            .updatedAt(Instant.EPOCH)
            .createdBy(0L)
            .updatedBy(0L)
            .build();
    }
}
