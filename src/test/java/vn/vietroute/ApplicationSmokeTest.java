package vn.vietroute;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.config.RootConfig;
import vn.vietroute.config.WebConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ApplicationSmokeTest {
    @Test
    void springMvcRendersThymeleafAndServesStaticAssetsWithoutDatabase() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext("src/main/webapp",
                    new org.springframework.core.io.FileSystemResourceLoader()));
            context.register(RootConfig.class, WebConfig.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            MvcResult home = mvc.perform(get("/")).andReturn();
            assertEquals(200, home.getResponse().getStatus());
            String html = home.getResponse().getContentAsString();
            assertTrue(html.contains("VietRoute"));
            assertTrue(html.contains("Quản lý nhà xe"));
            assertTrue(html.contains("href=\"/assets/css/app.css\""));
            assertTrue(!html.contains("th:text="), "Thymeleaf must render, not return the raw template");

            MvcResult stylesheet = mvc.perform(get("/assets/css/app.css")).andReturn();
            assertEquals(200, stylesheet.getResponse().getStatus());
            assertTrue(stylesheet.getResponse().getContentAsString().contains(".features"));

            MvcResult health = mvc.perform(get("/health")).andReturn();
            assertEquals(200, health.getResponse().getStatus());
            assertEquals("vietroute-web: UP", health.getResponse().getContentAsString());
        }
    }
}
