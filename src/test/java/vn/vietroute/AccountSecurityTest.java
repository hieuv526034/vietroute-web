package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Login vẫn CSRF/strict JSON; DTO gộp không để lộ bất kỳ trường mật khẩu nào. */
class AccountSecurityTest {

    @Test
    void requestSecretsAreWriteOnlyEvenIfDtoIsSerializedAccidentally() throws Exception {
        UserRequest value = new UserRequest();
        value.setPassword("secret-login");
        value.setCurrentPassword("secret-current");
        value.setNewPassword("secret-new");
        String json = new RootConfig().objectMapper().writeValueAsString(value);
        assertFalse(json.contains("secret-"));
        assertFalse(json.contains("password"));
    }

    @Test
    void loginKeepsCsrfAndRejectsUnknownActorFieldsBeforeDatabaseAccess() throws Exception {
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
            assertEquals(
                403,
                mvc
                    .perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var response = mvc
                .perform(
                    post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"user\",\"password\":\"secret-value\",\"actorId\":99}")
                )
                .andReturn()
                .getResponse();
            assertEquals(400, response.getStatus());
            assertFalse(response.getContentAsString().contains("secret-value"));
            // Email không thay thế username; cả hai dạng bị từ chối trước khi truy cập DB.
            for (String body : new String[] {
                "{\"email\":\"person@example.invalid\",\"password\":\"test-only-value\"}",
                "{\"username\":\"person@example.invalid\",\"password\":\"test-only-value\"}",
            }) {
                var emailLogin = mvc
                    .perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(body))
                    .andReturn()
                    .getResponse();
                assertEquals(400, emailLogin.getStatus());
                assertFalse(emailLogin.getContentAsString().contains("test-only-value"));
            }
        }
    }

    @Test
    void sourceDiagnosisNeverReturnsValuesAndWindowsBomIsAccepted() throws Exception {
        Path file = Files.createTempFile("auth-env-", ".txt");
        try {
            Files.writeString(file, "\uFEFFVIETROUTE_ENV=development\nVIETROUTE_DB_PASSWORD=private-test-value\n");
            var env = new ConfigurationEnvironment(key -> null, file.toString());
            assertEquals("LOCAL_FILE", env.sourceOf(Env.DB_PASSWORD));
            assertEquals("private-test-value", env.apply("VIETROUTE_DB_PASSWORD"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
