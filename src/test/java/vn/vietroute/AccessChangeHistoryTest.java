package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessChangeService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Kiểm projection/delta/trace và hai context; kiểm SQL/rollback thật dành cho PostgreSQL CI, không dùng Neon. */
class AccessChangeHistoryTest {

    @Test
    void changesRetainExactAddedAndRemovedCodesWithoutCopyingUnchangedPermissions() throws Exception {
        var fixture = new AccessTestConfig.HistoryFixture();
        var history = new AccessChangeService(fixture);
        history.mappings(
            null,
            Event.ROLE_PERMISSIONS,
            Target.ROLE,
            "OPERATOR",
            2,
            Set.of("CATALOG_READ", "CATALOG_CREATE"),
            Set.of("CATALOG_READ", "CATALOG_DELETE"),
            3,
            4
        );
        var event = fixture.events.get(0);
        assertEquals(Set.of("CATALOG_CREATE"), event.before().permissions());
        assertEquals(Set.of("CATALOG_DELETE"), event.after().permissions());
        assertTrue(event.before().delta());
        assertEquals(2, event.before().mappingCount());
        assertEquals(3, event.before().version());
        assertEquals(4, event.after().version());
        assertEquals(2, event.actorId());
        assertFalse(event.after().permissions().contains("CATALOG_READ"));
    }

    @Test
    void roleRemovalIncludingInactiveMappingAndServerTraceArePreserved() throws Exception {
        var fixture = new AccessTestConfig.HistoryFixture();
        var request = new MockHttpServletRequest();
        request.setAttribute(Http.TRACE_ATTRIBUTE, "history-server-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            new AccessChangeService(fixture).mappings(
                null,
                Event.USER_ROLES,
                Target.USER,
                "8",
                1,
                Set.of("INACTIVE_ROLE", "DRIVER"),
                Set.of("DRIVER", "CUSTOMER"),
                4,
                5
            );
            var event = fixture.events.get(0);
            assertEquals(Set.of("INACTIVE_ROLE"), event.before().roles());
            assertEquals(Set.of("CUSTOMER"), event.after().roles());
            assertEquals("history-server-trace", event.traceId());
            assertTrue(event.before().permissions().isEmpty());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void historyRetentionAndCapacityAreValidatedWithoutOpeningDb() {
        var defaults = new AccessSettings(key -> null);
        assertEquals(7, defaults.getAuditRetentionDays());
        assertEquals(1000, defaults.getAuditCapacity());
        assertThrows(IllegalStateException.class, () ->
            new AccessSettings(key -> key.equals(Env.ACCESS_AUDIT_RETENTION_DAYS.key()) ? "0" : null)
        );
        assertThrows(IllegalStateException.class, () ->
            new AccessSettings(key -> key.equals(Env.ACCESS_AUDIT_CAPACITY.key()) ? "99" : null)
        );
    }

    @Test
    void rootAndServletDenyDelegatedReadersAndValidateHistoryQuery() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            root.refresh();
            servlet.setServletContext(web);
            servlet.setParent(root);
            servlet.register(WebConfig.class);
            servlet.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(servlet).apply(springSecurity()).build();
            for (var role : List.of(Role.DRIVER, Role.ADMIN))
                for (boolean read : List.of(false, true)) {
                    var actor = UsernamePasswordAuthenticationToken.authenticated(
                        new UserPrincipal(2L, "history-test", Set.of(role), 0),
                        null,
                        (read ? List.of("ROLE_" + role.name(), "ACCESS_READ") : List.of("ROLE_" + role.name()))
                            .stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList()
                    );
                    assertEquals(
                        role.equals(Role.ADMIN) && read ? 200 : 403,
                        mvc
                            .perform(get("/api/v1/admin/access/history").with(authentication(actor)))
                            .andReturn()
                            .getResponse()
                            .getStatus()
                    );
                    if (role.equals(Role.ADMIN) && read) {
                        assertEquals(
                            400,
                            mvc
                                .perform(
                                    get("/api/v1/admin/access/history").param("size", "101").with(authentication(actor))
                                )
                                .andReturn()
                                .getResponse()
                                .getStatus()
                        );
                        assertEquals(
                            400,
                            mvc
                                .perform(
                                    get("/api/v1/admin/access/history")
                                        .param("actorId", "-1")
                                        .with(authentication(actor))
                                )
                                .andReturn()
                                .getResponse()
                                .getStatus()
                        );
                        assertEquals(
                            400,
                            mvc
                                .perform(
                                    get("/api/v1/admin/access/history")
                                        .param("event", "NOT_AN_EVENT")
                                        .with(authentication(actor))
                                )
                                .andReturn()
                                .getResponse()
                                .getStatus()
                        );
                        assertEquals(
                            400,
                            mvc
                                .perform(
                                    get("/api/v1/admin/access/history")
                                        .param("target", "x".repeat(121))
                                        .with(authentication(actor))
                                )
                                .andReturn()
                                .getResponse()
                                .getStatus()
                        );
                    }
                }
            assertEquals(401, mvc.perform(get("/api/v1/admin/access/history")).andReturn().getResponse().getStatus());
        }
    }
}
