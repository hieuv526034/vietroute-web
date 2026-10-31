package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.lifecycle.RegisterRequest;
import vn.vietroute.auth.dto.request.lifecycle.ResetPasswordRequest;
import vn.vietroute.auth.security.AccountSecrets;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Không provider/production: kiểm token, mã hóa, public/CSRF và JSON mass assignment trước service. */
class AccountSecurityTest {

    @TempDir
    Path directory;

    @Test
    void accountMailIsAuthenticatedEncryptedAndKeysAreSeparated() throws Exception {
        var first = new AccountSecrets(new AppSettings(Map.of(Env.JWT_SECRET.key(), "A".repeat(48))::get));
        var otherNode = new AccountSecrets(new AppSettings(Map.of(Env.JWT_SECRET.key(), "A".repeat(48))::get));
        var wrongKey = new AccountSecrets(new AppSettings(Map.of(Env.JWT_SECRET.key(), "B".repeat(48))::get));
        String token = first.token();
        assertTrue(token.matches(Regex.ACCOUNT_TOKEN));
        assertFalse(token.equals(first.token()));
        String encrypted = first.encrypt("recipient@example.invalid code=" + token);
        assertFalse(encrypted.contains(token));
        assertEquals("recipient@example.invalid code=" + token, otherNode.decrypt(encrypted));
        assertThrows(GeneralSecurityException.class, () -> wrongKey.decrypt(encrypted));
        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> first.decrypt(Base64.getEncoder().encodeToString(tampered)));
        assertEquals(64, first.hash(token).length());
    }

    @Test
    void requestSecretsAreWriteOnlyEvenIfDtoIsSerializedAccidentally() throws Exception {
        var json = new ObjectMapper();
        var register = new RegisterRequest();
        register.setPassword("UnitOnly@12345");
        register.setUsername("fixture");
        assertFalse(json.writeValueAsString(register).contains("UnitOnly"));
        var reset = json.readValue(
            "{\"email\":\"unit@example.invalid\",\"token\":\"abc\",\"newPassword\":\"UnitOnly@12345\"}",
            ResetPasswordRequest.class
        );
        assertEquals("abc", reset.getToken());
        String result = json.writeValueAsString(reset);
        assertFalse(result.contains("abc"));
        assertFalse(result.contains("UnitOnly"));
    }

    @Test
    void sourceDiagnosisNeverReturnsValuesAndWindowsBomIsAccepted() throws Exception {
        Path file = directory.resolve("test.env");
        Files.writeString(file, "\uFEFFVIETROUTE_DB_PASSWORD=unit-private\nVIETROUTE_DB_USER=file-user\n");
        var config = new ConfigurationEnvironment(Map.of(Env.DB_USER.key(), "IDE-private")::get, file.toString());
        assertEquals("unit-private", config.apply(Env.DB_PASSWORD.key()));
        assertEquals("LOCAL_FILE", config.sourceOf(Env.DB_PASSWORD));
        assertEquals("OS_IDE", config.sourceOf(Env.DB_USER));
        assertEquals("DEFAULT", config.sourceOf(Env.DB_URL));
    }

    @Test
    void publicAccountEndpointsKeepCsrfAndDenyClientRolesBeforeDatabaseAccess() throws Exception {
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
            assertEquals(
                200,
                mvc
                    .perform(get(Routes.AUTH + Routes.AUTH_OPTIONS))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(200, mvc.perform(get(Routes.AUTH_LIFECYCLE_PAGE)).andReturn().getResponse().getStatus());
            String body =
                "{\"username\":\"customerunit\",\"displayName\":\"Unit\",\"email\":\"unit@example.invalid\",\"password\":\"UnitOnly@12345\"}";
            assertEquals(
                403,
                mvc
                    .perform(
                        post(Routes.AUTH + Routes.AUTH_REGISTER)
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                503,
                mvc
                    .perform(
                        post(Routes.AUTH + Routes.AUTH_REGISTER)
                            .with(csrf())
                            .contentType("application/json")
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            String forged = body.substring(0, body.length() - 1) + ",\"roles\":[\"ADMIN\"]}";
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.AUTH + Routes.AUTH_REGISTER)
                            .with(csrf())
                            .contentType("application/json")
                            .content(forged)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                401,
                mvc
                    .perform(
                        post(Routes.AUTH + Routes.AUTH_EMAIL)
                            .with(csrf())
                            .contentType("application/json")
                            .content("{}")
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            var document = root
                .getBean(ObjectMapper.class)
                .readTree(mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse().getContentAsString());
            var security = document.at("/paths/~1api~1v1~1auth~1register/post/security");
            assertTrue(security.isMissingNode() || security.isEmpty());
            assertFalse(document.at("/paths/~1api~1v1~1auth~1email/post/security").isMissingNode());
        }
    }
}
