package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessAuthorization;
import vn.vietroute.auth.security.AccessCapabilities;
import vn.vietroute.auth.security.AccessFeatures;
import vn.vietroute.auth.security.AccessTargets;
import vn.vietroute.auth.security.ResourceEntitlements;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.business.catalog.service.CatalogService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Quyền explicit, không dùng helper nâng cấp legacy: chứng minh VIEW khác API và giữ guard ở hai context WAR. */
class AccessTreeTest {

    @Test
    void internalCallerCannotPassForgedAdminActorToBypassDelegationBounds() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(actor("ACCESS_ASSIGN"));
            var request = new RolePermissionsRequest();
            request.setPermissions(Set.of(Permission.SYSTEM_READ));
            request.setVersion(0L);
            var forged = new UserPrincipal(8L, "forged", Set.of(Role.ADMIN), 0);
            var error = assertThrows(BusinessException.class, () ->
                context.getBean(AccessService.class).replace(Role.valueOf("ENTRY"), request, forged)
            );
            assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void configurationReadIsScopedAndAssignmentVersionsAreRequired() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.SYSTEM_CONFIGURATION).with(authentication(actor("SYSTEM_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.SYSTEM_CONFIGURATION).with(authentication(actor("CONFIGURATION_READ"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (String path : List.of(Routes.ACCESS + "/roles/ENTRY/permissions", Routes.ACCESS + "/users/2/roles")) {
                String body = path.endsWith("permissions") ? "{\"permissions\":[]}" : "{\"roles\":[\"CUSTOMER\"]}";
                assertEquals(
                    400,
                    mvc
                        .perform(
                            put(path)
                                .with(csrf())
                                .with(authentication(actor("ACCESS_ASSIGN")))
                                .contentType("application/json")
                                .content(body)
                        )
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
            }
        }
    }

    @Test
    void allModeAndClassAdminRestrictionCannotBeWeakenedByMethodMetadata() throws Exception {
        var binding = AccessAuthorization.binding(
            new HandlerMethod(new Restricted(), Restricted.class.getMethod("both"))
        );
        assertFalse(AccessAuthorization.allows(binding, actor("CATALOG_READ", "CATALOG_UPDATE")));
        assertFalse(AccessAuthorization.allows(binding, actor("ROLE_ADMIN", "CATALOG_READ")));
        assertTrue(AccessAuthorization.allows(binding, actor("ROLE_ADMIN", "CATALOG_READ", "CATALOG_UPDATE")));
    }

    @AccessFunction(group = Groups.CATALOG, function = Functions.CATALOG, adminOnly = true)
    static class Restricted {

        @AccessFunction(
            group = Groups.CATALOG,
            permissions = { Permissions.CATALOG_READ, Permissions.CATALOG_UPDATE },
            match = AccessFunction.Match.ALL
        )
        public void both() {}
    }

    private UsernamePasswordAuthenticationToken actor(String... codes) {
        var result = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(8L, "entry", Set.of(Role.valueOf("ENTRY")), 0),
            null,
            Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()
        );
        result.setDetails(new ResourceEntitlements(Set.of(), Set.copyOf(Arrays.asList(codes)), null, null));
        return result;
    }

    @Test
    void viewAndCreateOnlyKeepUiAndDirectApiConsistentAcrossWarContexts() throws Exception {
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
            var entry = actor("CATALOG_VIEW", "CATALOG_CREATE");
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.ADMIN).with(authentication(entry)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.ADMIN).with(authentication(actor("CATALOG_CREATE"))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.ADMIN_CATALOGS)
                            .with(csrf())
                            .with(authentication(entry))
                            .contentType("application/json")
                            .content("{}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            for (var request : List.of(put(Routes.ADMIN_CATALOGS + "/1"), delete(Routes.ADMIN_CATALOGS + "/1"))) {
                var response = mvc
                    .perform(
                        request.with(csrf()).with(authentication(entry)).contentType("application/json").content("{}")
                    )
                    .andReturn()
                    .getResponse();
                assertEquals(403, response.getStatus());
                var json = root.getBean(ObjectMapper.class).readTree(response.getContentAsString());
                assertFalse(json.path("traceId").asText().isBlank());
            }
            assertEquals(
                403,
                mvc
                    .perform(get(Routes.ADMIN_CATALOGS + "/1").with(authentication(entry)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(
                        post(Routes.ADMIN_CATALOGS)
                            .with(csrf())
                            .with(authentication(actor("CATALOG_VIEW")))
                            .contentType("application/json")
                            .content("{}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            // Cả lời gọi bean không đi qua controller cũng không được xóa.
            SecurityContextHolder.getContext().setAuthentication(entry);
            assertThrows(AccessDeniedException.class, () ->
                root.getBean(CatalogService.class).delete(1L, 0L, (UserPrincipal) entry.getPrincipal(), Locale.ROOT)
            );
            SecurityContextHolder.clearContext();
            var capabilities = mvc
                .perform(get(Routes.AUTH + Routes.AUTH_CAPABILITIES).with(authentication(entry)))
                .andReturn()
                .getResponse();
            assertEquals(200, capabilities.getStatus());
            var catalog = root
                .getBean(ObjectMapper.class)
                .readTree(capabilities.getContentAsString())
                .path("data")
                .findParents("function")
                .stream()
                .filter(item -> item.path("function").asText().equals("CATALOG"))
                .findFirst()
                .orElseThrow();
            assertTrue(catalog.path("view").asBoolean());
            assertTrue(catalog.path("actions").toString().contains("CREATE"));
            assertFalse(catalog.path("actions").toString().contains("UPDATE"));
            var tree = mvc
                .perform(get(Routes.ACCESS + Routes.ACCESS_TREE).with(authentication(actor("ACCESS_READ"))))
                .andReturn()
                .getResponse();
            assertEquals(200, tree.getStatus());
            assertTrue(tree.getContentAsString().contains("CATALOG_VIEW"));
            assertTrue(tree.getContentAsString().contains("POST /api/v1/admin/catalogs"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void policiesAndWriteCompatibilityDoNotInventOtherActionCapabilities() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, WebConfig.class);
            context.refresh();
            var inventory = AccessTargets.discover(context.getBean(RequestMappingHandlerMapping.class));
            var policy = new AccessPolicyResponse(
                1,
                "ENDPOINT",
                "PUT /api/v1/admin/catalogs/{id}",
                "Chặn sửa",
                "Block update",
                false,
                Set.of(),
                Set.of(),
                0,
                Instant.EPOCH,
                0
            );
            var result = AccessCapabilities.compute(inventory, actor("CATALOG_VIEW", "CATALOG_WRITE"), List.of(policy))
                .stream()
                .filter(item -> item.function().equals("CATALOG"))
                .findFirst()
                .orElseThrow();
            assertTrue(result.actions().contains("CREATE"));
            assertFalse(result.actions().contains("UPDATE"));
            assertFalse(result.actions().contains("READ"));
            assertFalse(result.targets().contains(policy.target()));
        }
    }

    @Test
    void missingEndpointMetadataAndInvalidTreesFailBeforePublishing() {
        var mapping = new RequestMappingHandlerMapping();
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MissingGuard.class);
            context.refresh();
            mapping.setApplicationContext(context);
            mapping.afterPropertiesSet();
            assertThrows(IllegalStateException.class, () -> AccessTargets.discover(mapping));
        }
        var a = new AccessFeatures.Feature("A", "B", "a", null, null);
        var b = new AccessFeatures.Feature("B", "A", "b", null, null);
        assertThrows(IllegalStateException.class, () -> AccessFeatures.validate(List.of(a, b)));
        assertThrows(IllegalStateException.class, () -> AccessFeatures.validate(List.of(a, a)));
        assertThrows(IllegalStateException.class, () -> AccessFeatures.validate(List.of(a)));
    }

    @RestController
    static class MissingGuard {

        @GetMapping("/private-missing")
        public String missing() {
            return "closed";
        }
    }
}
