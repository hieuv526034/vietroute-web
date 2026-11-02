package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.RoleService;
import vn.vietroute.auth.service.UserService;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.support.AccessTestConfig;

/** Kiểm quyền UI/API và whitelist cấu hình trước khi mở DB; SQL integration chạy trên PostgreSQL CI tạm. */
class SystemManagementTest {

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
        context.register(RootConfig.class, AccessTestConfig.class, WebConfig.class);
        context.refresh();
        return context;
    }

    private UsernamePasswordAuthenticationToken actor(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(
                2L,
                "isolated",
                Set.of(List.of(authorities).contains("ROLE_ADMIN") ? "ADMIN" : "OPERATOR"),
                0
            ),
            null,
            AccessTestConfig.upgradedAuthorities(authorities)
        );
    }

    @Test
    void managementPagesRequireTheirOwnPermissionAndRenderBothLocalesWithoutDb() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            Map<String, String> pages = Map.of(
                Routes.ACCESS_PAGE,
                "GET " + Routes.ACCESS_PAGE,
                Routes.API_JOURNAL_PAGE,
                "GET " + Routes.API_JOURNAL_PAGE
            );
            for (var entry : pages.entrySet()) {
                assertEquals(302, mvc.perform(get(entry.getKey())).andReturn().getResponse().getStatus());
                assertEquals(
                    403,
                    mvc
                        .perform(get(entry.getKey()).with(authentication(actor("CATALOG_READ"))))
                        .andReturn()
                        .getResponse()
                        .getStatus()
                );
                for (String locale : List.of("vi", "en")) {
                    var response = mvc
                        .perform(
                            get(entry.getKey())
                                .param("lang", locale)
                                .with(authentication(actor(entry.getValue())))
                        )
                        .andReturn()
                        .getResponse();
                    assertEquals(200, response.getStatus());
                    String html = response.getContentAsString();
                    assertTrue(
                        html.contains(
                            entry.getKey().equals(Routes.ACCESS_PAGE) ? "data-role-workspace" : "id=\"api-journal\""
                        )
                    );
                    assertTrue(
                        html.contains(entry.getKey().equals(Routes.ACCESS_PAGE) ? "roles-controls" : "journal-controls")
                    );
                    assertFalse(html.contains("th:text="));
                    assertFalse(html.contains("??ui.ops"));
                }
            }
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    @Test
    void delegatedAccessReadCanEnumerateAccountsAndDirectServicesEnforcePermissions() {
        try (var context = context()) {
            var access = context.getBean(UserService.class);
            SecurityContextHolder.getContext().setAuthentication(actor("ROLE_ADMIN"));
            assertThrows(BusinessException.class, () -> access.search(new UserSearch()));
            SecurityContextHolder.getContext().setAuthentication(actor("ACCESS_READ"));
            var approved = context
                .getBean(EndpointAccess.class)
                .authorize(actor("ACCESS_READ"), "GET /api/v1/admin/access/users", false);
            SecurityContextHolder.getContext().setAuthentication(approved);
            assertEquals(0, access.search(new UserSearch()).getTotalElements());
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void invalidFiltersAndPaginationFailBeforeAnyDatabaseRead() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var admin = actor("ROLE_ADMIN", "ACCESS_READ", "JOB_READ");
            var filter = mvc
                .perform(
                    get(Routes.ACCESS + Routes.USERS_ACTION)
                        .param("username", "x".repeat(161))
                        .with(authentication(admin))
                )
                .andReturn()
                .getResponse();
            assertEquals(400, filter.getStatus());
            assertTrue(filter.getContentAsString().contains("validation.length"));
            assertEquals(
                400,
                mvc
                    .perform(
                        get(Routes.ACCESS + Routes.USERS_ACTION)
                            .param("size", "101")
                            .with(authentication(admin))
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertNull(context.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }
}
