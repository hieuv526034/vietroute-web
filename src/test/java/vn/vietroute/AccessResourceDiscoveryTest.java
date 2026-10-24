package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.AccessTargets;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Dựng root/servlet như WAR; không bật migration và không mở connection DB thật. */
class AccessResourceDiscoveryTest {

    @Test
    void discoversRealMappingsAndKeepsPublicAndRecoveryRoutesReadOnly() throws Exception {
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
            var mapping = child.getBean(RequestMappingHandlerMapping.class);
            var resources = AccessTargets.discover(mapping);
            var catalog = resources
                .stream()
                .filter(item -> item.target().equals("PUT /api/v1/admin/catalogs/{id}"))
                .findFirst()
                .orElseThrow();
            assertEquals("CATALOG", catalog.group());
            assertTrue(catalog.declaredPermissions().contains("CATALOG_UPDATE"));
            assertTrue(catalog.declaredPermissions().contains("CATALOG_WRITE"));
            var media = resources
                .stream()
                .filter(item -> item.target().equals("GET " + Routes.MEDIA_PAGE))
                .toList();
            assertEquals(1, media.size(), "Không sinh endpoint giả trùng trang giao diện");
            assertEquals("MENU", media.get(0).kind());
            assertTrue(media.get(0).configurable());
            assertTrue(media.get(0).handler().startsWith("MediaPageController#"));
            for (String path : List.of(Routes.LOGIN, Routes.HOME, Routes.HEALTH, Routes.OPENAPI, Routes.AUTH_LOGIN)) {
                var found = resources
                    .stream()
                    .filter(item -> item.target().endsWith(" " + path))
                    .findFirst()
                    .orElseThrow();
                assertTrue(found.publicRoute(), path);
                assertFalse(found.configurable(), path);
            }
            assertTrue(
                resources
                    .stream()
                    .filter(item -> item.target().contains(Routes.ACCESS))
                    .noneMatch(AccessResourceResponse::configurable)
            );
            assertFalse(
                AccessTargets.inventory(mapping)
                    .stream()
                    .anyMatch(item -> item.target().endsWith(" " + Routes.AUTH_LOGIN))
            );
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            var spec = root
                .getBean(ObjectMapper.class)
                .readTree(mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse().getContentAsString());
            var schema = spec.at(
                "/paths/~1api~1v1~1admin~1access~1resources/get/responses/200/content/application~1json/schema"
            );
            String ref = schema.path("$ref").asText();
            assertTrue(ref.contains("ApiResponse"), schema.toString());
            var envelope = spec.at(ref.substring(1));
            assertTrue(envelope.path("properties").has("data"));
            assertTrue(envelope.path("properties").has("traceId"));
            // ACCESS_READ được ủy quyền qua DB; chỉ có role ADMIN chưa đủ, kể cả gọi trực tiếp URL API.
            for (var scenario : List.of(
                List.of("ROLE_ADMIN"),
                List.of("ACCESS_READ"),
                List.of("ROLE_ADMIN", "ACCESS_READ")
            )) {
                var principal = new UserPrincipal(1L, "fixture", Set.of(Role.ADMIN), 0);
                var actor = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    scenario.stream().map(SimpleGrantedAuthority::new).toList()
                );
                assertEquals(
                    scenario.contains("ACCESS_READ") ? 200 : 403,
                    mvc
                        .perform(get(Routes.ACCESS + Routes.ACCESS_RESOURCES).with(authentication(actor)))
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
                if (scenario.contains("ACCESS_READ")) {
                    for (var invalid : Map.of("kind", "INVALID", "query", "a".repeat(161)).entrySet()) {
                        assertEquals(
                            400,
                            mvc
                                .perform(
                                    get(Routes.ACCESS + Routes.ACCESS_RESOURCES)
                                        .param(invalid.getKey(), invalid.getValue())
                                        .with(authentication(actor))
                                )
                                .andReturn()
                                .getResponse()
                                .getStatus()
                        );
                    }
                }
            }
        }
    }

    @Test
    void synchronizesOnlyOwningServletContextOnceAndHonorsDisabledFlag() {
        try (var context = new StaticApplicationContext(); var unrelated = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("mapping", new RequestMappingHandlerMapping());
            var recorder = new RecordingResources();
            var enabled = new AppSettings(Map.of(Env.ACCESS_SYNC_ENABLED.key(), "true")::get);
            var listener = new AccessResourceSynchronizer(context, enabled, recorder);
            listener.onApplicationEvent(new ContextRefreshedEvent(unrelated));
            assertEquals(0, recorder.calls);
            listener.onApplicationEvent(new ContextRefreshedEvent(context));
            listener.onApplicationEvent(new ContextRefreshedEvent(context));
            assertEquals(1, recorder.calls);
            new AccessResourceSynchronizer(context, new AppSettings(key -> null), recorder).onApplicationEvent(
                new ContextRefreshedEvent(context)
            );
            assertEquals(1, recorder.calls);
        }
    }

    @Test
    void synchronizationFollowsInitializerUnlessExplicitlyConfigured() {
        var values = Map.of(Env.DB_INIT_ENABLED.key(), "true", Env.BOOTSTRAP_ADMIN_PASSWORD.key(), "UnitOnly@12345");
        assertTrue(new AppSettings(values::get).isAccessSyncEnabled());
        assertFalse(
            new AppSettings(key ->
                key.equals(Env.ACCESS_SYNC_ENABLED.key()) ? "false" : values.get(key)
            ).isAccessSyncEnabled()
        );
    }

    private static class RecordingResources extends AccessResourceService {

        private int calls;

        RecordingResources() {
            super(null, null, null);
        }

        @Override
        public void synchronize(List<AccessResourceResponse> inventory) {
            calls++;
        }
    }
}
