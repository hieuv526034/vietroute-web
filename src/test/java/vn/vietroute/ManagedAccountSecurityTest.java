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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.ManagedAccountCreateRequest;
import vn.vietroute.auth.dto.request.ManagedAccountSearchRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.ManagedAccountResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.ManagedAccountRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.auth.service.ManagedAccountService;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Regression hai context WAR, preview policy của user đích và phiên bắt đổi mật khẩu; không mở DB/provider. */
class ManagedAccountSecurityTest {

    @Test
    void accountAdministrationRequiresAdminAndTheSpecificActionInBothContexts() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, AccountPages.class);
            root.refresh();
            child.setServletContext(web);
            child.setParent(root);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var delegated = actor("ACCOUNT_VIEW", "ACCOUNT_READ", "ACCOUNT_CREATE", "ACCOUNT_UPDATE", "ACCESS_ASSIGN");
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.ACCOUNTS_PAGE).with(authentication(delegated)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.ACCOUNTS).with(authentication(delegated)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        post(Routes.ACCOUNTS)
                            .with(authentication(delegated))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        put(Routes.ACCOUNTS + "/9/state")
                            .with(authentication(delegated))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"active\":false,\"version\":0}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            SecurityContextHolder.getContext().setAuthentication(delegated);
            assertThrows(AccessDeniedException.class, () ->
                root.getBean(ManagedAccountService.class).page(new ManagedAccountSearchRequest())
            );
            SecurityContextHolder.clearContext();
            var adminRead = actor("ROLE_ADMIN", "ACCOUNT_READ");
            for (String role : List.of("", "DRIVER"))
                assertEquals(
                    200,
                    mvc
                        .perform(
                            get(Routes.ACCOUNTS).param("role", role).param("active", "").with(authentication(adminRead))
                        )
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
            assertEquals(
                400,
                mvc
                    .perform(get(Routes.ACCOUNTS).param("role", "bad!").with(authentication(adminRead)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var adminView = actor("ROLE_ADMIN", "ACCOUNT_VIEW");
            var html = mvc
                .perform(get(Routes.ACCOUNTS_PAGE).with(authentication(adminView)))
                .andReturn()
                .getResponse();
            assertEquals(200, html.getStatus());
            assertTrue(html.getContentAsString().contains("account-edit-dialog"));
            var createOnly = actor("ROLE_ADMIN", "ACCOUNT_CREATE");
            assertEquals(
                403,
                mvc
                    .perform(
                        post(Routes.ACCOUNTS)
                            .with(authentication(createOnly))
                            .with(csrf())
                            .contentType("application/json")
                            .content("{}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var admin = actor("ROLE_ADMIN", "ACCOUNT_CREATE", "ACCESS_ASSIGN");
            String body =
                "{\"username\":\"driverunit\",\"displayName\":\"Driver\",\"password\":\"UnitOnly@12345\",\"roles\":[\"DRIVER\"]}";
            assertEquals(
                403,
                mvc
                    .perform(
                        post(Routes.ACCOUNTS).with(authentication(admin)).contentType("application/json").content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.ACCOUNTS)
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body.substring(0, body.length() - 1) + ",\"createdBy\":0}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.ACCOUNTS)
                            .with(authentication(admin))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body.replace("[\"DRIVER\"]", "[]"))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void temporaryPasswordSessionHasNoAuthoritiesUntilPasswordChange() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, TemporaryUsers.class);
            root.refresh();
            child.setServletContext(web);
            child.setParent(root);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var token = root.getBean(JwtService.class).issue(fixture(true)).accessToken();
            var blocked = mvc
                .perform(get(Routes.ADMIN_CATALOGS + "/1").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse();
            assertEquals(403, blocked.getStatus());
            assertTrue(blocked.getContentAsString().contains("AUTH011"));
            assertEquals(
                302,
                mvc
                    .perform(get(Routes.ADMIN).header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.ACCOUNT).header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.AUTH + Routes.ME_ACTION).header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var caps = mvc
                .perform(get(Routes.AUTH + Routes.AUTH_CAPABILITIES).header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse();
            assertEquals(200, caps.getStatus());
            for (var item : root.getBean(ObjectMapper.class).readTree(caps.getContentAsString()).path("data")) {
                assertFalse(item.path("view").asBoolean());
                assertEquals(0, item.path("actions").size());
            }
        }
    }

    @Test
    void previewLoadsPoliciesForTargetUserInsteadOfAdminsBypassedSnapshot() {
        var registry = new AccessTestConfig.PolicyFixture();
        registry.rules = List.of(
            new AccessPolicyResponse(
                1,
                "MENU",
                "GET /admin",
                "Chặn",
                "Blocked",
                false,
                Set.of(),
                Set.of(),
                0,
                Instant.EPOCH,
                0
            )
        );
        var repository = new UserRepository(null) {
            @Override
            public UserAccount findById(Long id) {
                return fixture(false);
            }
        };
        var service = new ManagedAccountService(
            new ManagedAccountRepository(null) {
                @Override
                public Set<Role> assignedRoles(long id) {
                    return Set.of(Role.DRIVER);
                }
            },
            repository,
            null,
            null,
            null,
            null,
            new AccessManagementService(registry, null, null)
        );
        var endpoint = new AccessResourceResponse(
            "MENU",
            "GET /admin",
            "Test#page",
            "Catalog",
            false,
            true,
            Instant.EPOCH,
            Instant.EPOCH,
            "CATALOG",
            List.of("CATALOG_VIEW"),
            "CATALOG",
            "ANY",
            false
        );
        var preview = service.effective(8, List.of(endpoint));
        assertFalse(
            preview
                .capabilities()
                .stream()
                .filter(item -> item.function().equals("CATALOG"))
                .findFirst()
                .orElseThrow()
                .view()
        );
        registry.rules = List.of();
        assertTrue(
            service
                .effective(8, List.of(endpoint))
                .capabilities()
                .stream()
                .filter(item -> item.function().equals("CATALOG"))
                .findFirst()
                .orElseThrow()
                .view()
        );
    }

    @Test
    void managedPasswordIsWriteOnlyAndNotReturnedInResponses() throws Exception {
        var request = new ManagedAccountCreateRequest();
        request.setPassword("UnitOnly@12345");
        request.setUsername("driverunit");
        assertFalse(new ObjectMapper().writeValueAsString(request).contains("UnitOnly"));
    }

    private static UsernamePasswordAuthenticationToken actor(String... codes) {
        boolean admin = List.of(codes).contains("ROLE_ADMIN");
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(8L, "staffunit", Set.of(admin ? Role.ADMIN : Role.OPERATOR), 0),
            null,
            List.of(codes).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private static UserAccount fixture(boolean temporary) {
        return UserAccount.builder()
            .id(8L)
            .username("driverunit")
            .displayName("Driver test")
            .active(true)
            .mustChangePassword(temporary)
            .roles(Set.of(Role.DRIVER))
            .permissions(Set.of(Permission.CATALOG_VIEW, Permission.CATALOG_CREATE))
            .createdAt(Instant.EPOCH)
            .updatedAt(Instant.EPOCH)
            .createdBy(0L)
            .updatedBy(0L)
            .build();
    }

    /** Chỉ thay DAO trang, giữ binding và toàn bộ guard thật cho bộ lọc rỗng/không hợp lệ. */
    @Configuration
    static class AccountPages {

        @Bean
        @Primary
        ManagedAccountRepository accountPages() {
            return new ManagedAccountRepository(null) {
                @Override
                public PageResponse<ManagedAccountResponse> page(ManagedAccountSearchRequest page) {
                    return PageResponse.of(List.of(), page, 0);
                }
            };
        }
    }

    @Configuration
    static class TemporaryUsers {

        @Bean
        @Primary
        UserRepository temporaryUsers() {
            return new UserRepository(null) {
                @Override
                public UserAccount findById(Long id) {
                    return fixture(true);
                }
            };
        }
    }
}
