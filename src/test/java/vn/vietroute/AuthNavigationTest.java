package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Kiểm nút Home và URL login với root/servlet tách biệt; không dùng DB hoặc tạo phiên đăng nhập thật. */
class AuthNavigationTest {

    @Test
    void homeAndLoginRespectPrincipalLocaleAndWarContext() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            root.refresh();
            servlet.setParent(root);
            servlet.setServletContext(web);
            servlet.register(WebConfig.class);
            servlet.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(servlet).apply(springSecurity()).build();
            for (String contextPath : List.of("", "/vietroute")) {
                for (String locale : List.of("vi", "en")) {
                    var home = mvc
                        .perform(
                            get(contextPath + "/")
                                .contextPath(contextPath)
                                .param("lang", locale)
                        )
                        .andReturn()
                        .getResponse();
                    assertCta(home.getContentAsString(StandardCharsets.UTF_8), contextPath + "/login?lang=" + locale);
                    assertEquals(
                        200,
                        mvc
                            .perform(
                                get(contextPath + "/login")
                                    .contextPath(contextPath)
                                    .param("lang", locale)
                            )
                            .andReturn()
                            .getResponse()
                            .getStatus()
                    );
                    for (Role role : List.of(Role.ADMIN, Role.CUSTOMER)) {
                        var actor = UsernamePasswordAuthenticationToken.authenticated(
                            new UserPrincipal(1L, "testuser", Set.of(role), 0),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                        );
                        String destination =
                            contextPath + (role == Role.ADMIN ? "/admin" : "/account") + "?lang=" + locale;
                        var authenticatedHome = mvc
                            .perform(
                                get(contextPath + "/")
                                    .contextPath(contextPath)
                                    .param("lang", locale)
                                    .with(authentication(actor))
                            )
                            .andReturn()
                            .getResponse();
                        assertEquals(200, authenticatedHome.getStatus());
                        assertCta(authenticatedHome.getContentAsString(StandardCharsets.UTF_8), destination);
                        var login = mvc
                            .perform(
                                get(contextPath + "/login")
                                    .contextPath(contextPath)
                                    .param("lang", locale)
                                    .with(authentication(actor))
                            )
                            .andReturn()
                            .getResponse();
                        assertEquals(302, login.getStatus());
                        assertEquals(destination, login.getRedirectedUrl());
                        // Trang sau đổi mật khẩu phải render được thông báo vi/en dưới cả ROOT và context WAR.
                        var account = mvc
                            .perform(
                                get(contextPath + "/account")
                                    .contextPath(contextPath)
                                    .param("lang", locale)
                                    .param("passwordChanged", "true")
                                    .with(authentication(actor))
                            )
                            .andReturn()
                            .getResponse();
                        assertEquals(200, account.getStatus());
                        String accountHtml = account.getContentAsString(StandardCharsets.UTF_8);
                        assertTrue(accountHtml.contains("data-password-changed=\""));
                        assertTrue(!accountHtml.contains("??success.passwordChanged"));
                    }
                }
            }
            assertNull(root.getBean(HikariDataSource.class).getHikariPoolMXBean());
        }
    }

    private void assertCta(String html, String destination) {
        int start = html.indexOf("id=\"home-workspace\"");
        assertTrue(start >= 0);
        assertTrue(html.substring(start, html.indexOf("</a>", start)).contains("href=\"" + destination + "\""));
    }
}
