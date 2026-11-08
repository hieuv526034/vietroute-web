package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.support.AccessTestConfig;

class ApplicationSmokeTest {

    @Test
    void springMvcRendersThymeleafAndServesStaticAssetsWithoutDatabase() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
            context.register(RootConfig.class, AccessTestConfig.class, WebConfig.class);
            context.refresh();
            assertTrue(context.getBean(HikariDataSource.class).getHikariPoolMXBean() == null);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            MvcResult home = mvc.perform(get("/").header("Accept-Language", "en-US,en;q=0.9")).andReturn();
            assertEquals(200, home.getResponse().getStatus());
            assertEquals("vi", RequestContextUtils.getLocale(home.getRequest()).getLanguage());
            String html = home.getResponse().getContentAsString();
            assertTrue(html.contains("VietRoute"));
            assertTrue(html.contains("Quản lý nhà xe"), html);
            assertTrue(html.contains("href=\"/assets/css/app.css\""));
            assertTrue(!html.contains("th:text="), "Thymeleaf must render, not return the raw template");
            assertFalse(html.contains("/account-access"), "Trang chủ không giữ link đăng ký/recovery đã gỡ");

            MvcResult stylesheet = mvc.perform(get("/assets/css/app.css")).andReturn();
            assertEquals(200, stylesheet.getResponse().getStatus());
            assertTrue(stylesheet.getResponse().getContentAsString().contains(".features"));

            MvcResult health = mvc.perform(get("/health")).andReturn();
            assertEquals(200, health.getResponse().getStatus());
            assertEquals("vietroute-web: UP", health.getResponse().getContentAsString());

            MvcResult english = mvc.perform(get("/").param("lang", "en")).andReturn();
            assertTrue(english.getResponse().getContentAsString().contains("Manage your fleet"));
            assertEquals("en", english.getResponse().getHeader("Content-Language"));

            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    new UserPrincipal(7L, "contractadmin", Set.of("ADMIN"), 0),
                    null,
                    List.of(
                        new SimpleGrantedAuthority("GET /docs"),
                        new SimpleGrantedAuthority("GET /v3/api-docs"),
                        new SimpleGrantedAuthority("GET /dashboard"),
                        new SimpleGrantedAuthority("GET /admin")
                    )
                )
            );
            MvcResult docs = mvc.perform(get("/v3/api-docs")).andReturn();
            assertEquals(200, docs.getResponse().getStatus());
            String spec = docs.getResponse().getContentAsString();
            var document = context.getBean(ObjectMapper.class).readTree(spec);
            assertTrue(document.isObject(), "OpenAPI phải trả JSON object, không encode thành JSON string");
            assertTrue(document.get("paths").has("/api/v1/auth/change-password"));
            assertTrue(document.get("components").get("schemas").has("CategoryResponse"));
            assertTrue(document.at("/paths/~1api~1v1~1admin~1categories/post/responses").has("201"));
            assertFalse(document.get("paths").has("/api/v1/admin/mail"));
            assertFalse(document.get("paths").has("/api/v1/media"));
            assertTrue(document.get("paths").has("/api/v1/admin/access/roles/{role}/functions"));
            var errorExample = document.at(
                "/paths/~1api~1v1~1auth~1login/post/responses/401/content/application~1json/example"
            );
            assertFalse(errorExample.get("success").asBoolean());
            assertEquals("AUTH002", errorExample.get("code").asText());
            assertTrue(spec.contains("/api/v1/auth/login"));
            assertTrue(spec.contains("/api/v1/admin/categories"));
            assertTrue(spec.contains("createdAt"));
            assertTrue(spec.contains("PageResponseCategoryLookupResponse"));

            assertEquals(200, mvc.perform(get("/docs")).andReturn().getResponse().getStatus());
            for (String path : List.of("/dashboard", "/admin")) {
                var overview = mvc.perform(get(path).param("lang", "en")).andReturn().getResponse();
                assertEquals(200, overview.getStatus(), path);
                assertTrue(overview.getContentAsString().contains("overview-hero"), path);
                assertTrue(overview.getContentAsString().contains("overview-module"), path);
            }
            SecurityContextHolder.clearContext();
            assertEquals(200, mvc.perform(get("/login")).andReturn().getResponse().getStatus());
            assertEquals(
                200,
                mvc.perform(get("/webjars/axios/1.20.0/dist/axios.min.js")).andReturn().getResponse().getStatus()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
