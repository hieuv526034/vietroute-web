package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Chức năng phụ được chủ dự án cắt trong AUTHDB04: URL cũ không được khôi phục quyền hoặc gửi mail. */
class AccountMailTemplateTest {

    @Test
    void removedEndpoint0NeverInvokesTheOldFlow() throws Exception {
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
            var response = mvc
                .perform(
                    post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"roles\":[\"ADMIN\"],\"password\":\"private-value\"}")
                )
                .andReturn()
                .getResponse();
            assertTrue(response.getStatus() == 404 || response.getStatus() == 401);
            assertFalse(response.getContentAsString().contains("private-value"));
        }
    }

    @Test
    void removedEndpoint1NeverInvokesTheOldFlow() throws Exception {
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
            var response = mvc
                .perform(
                    post("/api/v1/auth/resend")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"roles\":[\"ADMIN\"],\"password\":\"private-value\"}")
                )
                .andReturn()
                .getResponse();
            assertTrue(response.getStatus() == 404 || response.getStatus() == 401);
            assertFalse(response.getContentAsString().contains("private-value"));
        }
    }

    @Test
    void removedEndpoint2NeverInvokesTheOldFlow() throws Exception {
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
            var response = mvc
                .perform(
                    post("/api/v1/auth/forgot-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"roles\":[\"ADMIN\"],\"password\":\"private-value\"}")
                )
                .andReturn()
                .getResponse();
            assertTrue(response.getStatus() == 404 || response.getStatus() == 401);
            assertFalse(response.getContentAsString().contains("private-value"));
        }
    }
}
