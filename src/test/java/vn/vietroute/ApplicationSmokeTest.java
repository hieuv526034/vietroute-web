package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.WebConfig;

class ApplicationSmokeTest {

    @Test
    void springMvcRendersThymeleafAndServesStaticAssetsWithoutDatabase() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(
                new MockServletContext("src/main/webapp", new org.springframework.core.io.FileSystemResourceLoader())
            );
            context.register(RootConfig.class, WebConfig.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            MvcResult home = mvc.perform(get("/")).andReturn();
            assertEquals(200, home.getResponse().getStatus());
            assertEquals(
                "vi",
                org.springframework.web.servlet.support.RequestContextUtils.getLocale(home.getRequest()).getLanguage()
            );
            String html = home.getResponse().getContentAsString();
            assertTrue(html.contains("VietRoute"));
            assertTrue(html.contains("Quản lý nhà xe"), html);
            assertTrue(html.contains("href=\"/assets/css/app.css\""));
            assertTrue(!html.contains("th:text="), "Thymeleaf must render, not return the raw template");

            MvcResult stylesheet = mvc.perform(get("/assets/css/app.css")).andReturn();
            assertEquals(200, stylesheet.getResponse().getStatus());
            assertTrue(stylesheet.getResponse().getContentAsString().contains(".features"));

            MvcResult health = mvc.perform(get("/health")).andReturn();
            assertEquals(200, health.getResponse().getStatus());
            assertEquals("vietroute-web: UP", health.getResponse().getContentAsString());

            MvcResult english = mvc.perform(get("/").param("lang", "en")).andReturn();
            assertTrue(english.getResponse().getContentAsString().contains("Manage your fleet"));
            assertEquals("en", english.getResponse().getHeader("Content-Language"));

            MvcResult docs = mvc.perform(get("/v3/api-docs")).andReturn();
            assertEquals(200, docs.getResponse().getStatus());
            String spec = docs.getResponse().getContentAsString();
            var document = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).readTree(spec);
            assertTrue(document.isObject(), "OpenAPI phải trả JSON object, không encode thành JSON string");
            assertTrue(document.get("paths").has("/api/v1/auth/change-password"));
            assertTrue(document.get("components").get("schemas").has("CatalogResponse"));
            var errorExample = document.at(
                "/paths/~1api~1v1~1auth~1login/post/responses/401/content/application~1json/example"
            );
            assertFalse(errorExample.get("success").asBoolean());
            assertEquals("AUTH002", errorExample.get("code").asText());
            assertTrue(spec.contains("/api/v1/auth/login"));
            assertTrue(spec.contains("/api/v1/admin/catalogs"));
            assertTrue(spec.contains("createdAt"));
            assertTrue(spec.contains("PageResponseCatalogResponse"));

            assertEquals(200, mvc.perform(get("/login")).andReturn().getResponse().getStatus());
            assertEquals(200, mvc.perform(get("/docs")).andReturn().getResponse().getStatus());
            assertEquals(
                200,
                mvc.perform(get("/webjars/axios/1.20.0/dist/axios.min.js")).andReturn().getResponse().getStatus()
            );
        }
    }
}
