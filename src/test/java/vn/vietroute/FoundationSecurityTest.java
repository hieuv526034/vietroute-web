package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.service.CatalogItemService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.AccessTestConfig;

/** Kiểm tra biên bảo mật/locale mà không kết nối DB; nghiệp vụ SQL kiểm tra bằng PostgreSQL tạm ở CI. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationSecurityTest {

    private AnnotationConfigWebApplicationContext context;

    private MockMvc mvc;

    private ObjectMapper json;

    @BeforeAll
    void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
        context.register(RootConfig.class, AccessTestConfig.class, WebConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        json = context.getBean(ObjectMapper.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void protectedApiUsesLocalizedEnvelopeAndTraceId() throws Exception {
        var vi = mvc.perform(get("/api/v1/auth/me").header("X-Locale", "vi")).andReturn().getResponse();
        var en = mvc.perform(get("/api/v1/auth/me").header("X-Locale", "en")).andReturn().getResponse();
        assertEquals(401, vi.getStatus());
        assertEquals(401, en.getStatus());
        JsonNode first = json.readTree(vi.getContentAsString(StandardCharsets.UTF_8));
        JsonNode second = json.readTree(en.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(first.get("code"), second.get("code"));
        assertEquals("auth.unauthenticated", first.get("messageKey").asText());
        assertNotEquals(first.get("message"), second.get("message"));
        assertEquals(first.get("traceId").asText(), vi.getHeader("X-Request-ID"));
    }

    @Test
    void cookieMutationsRequireCsrfEvenBeforeLogin() throws Exception {
        var result = mvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"username\":\"admin\",\"password\":\"anything\"}")
            )
            .andReturn()
            .getResponse();
        assertEquals(403, result.getStatus());
        assertEquals("AUTH006", json.readTree(result.getContentAsString()).get("code").asText());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(2L, "testuser", Set.of("OPERATOR"), 0),
                null,
                List.of(new SimpleGrantedAuthority(authority))
            )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jsonBodyLimitAlsoAppliesWhenContentLengthDoesNotDeclareTheFullBody() throws Exception {
        for (String contentType : List.of("application/json", "application/problem+json")) {
            var response = mvc
                .perform(post("/api/v1/auth/login").with(csrf()).contentType(contentType).content(" ".repeat(65537)))
                .andReturn()
                .getResponse();
            assertEquals(413, response.getStatus());
            assertTrue(response.getContentAsString().contains("COM014"));
            assertFalse(response.getContentAsString().contains(" ".repeat(100)));
        }
    }

    @Test
    void invalidJwtIsRejectedBeforeAccessingDatabase() throws Exception {
        var response = mvc
            .perform(get("/api/v1/auth/me").header("Authorization", "Bearer bad.token.value"))
            .andReturn()
            .getResponse();
        assertEquals(401, response.getStatus());
        assertEquals("AUTH004", json.readTree(response.getContentAsString()).get("code").asText());
    }

    @Test
    void validationDoesNotEchoPasswordOrUnknownAuditFields() throws Exception {
        var invalid = mvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"username\":\"invalid user!\",\"password\":\"secret-never-echo\"}")
            )
            .andReturn()
            .getResponse();
        assertEquals(400, invalid.getStatus());
        String body = invalid.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("secret-never-echo"));
        assertTrue(body.contains("validation.username"));
        var audit = mvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"username\":\"admin\",\"password\":\"test\",\"createdBy\":1}")
            )
            .andReturn()
            .getResponse();
        assertEquals(400, audit.getStatus());
        assertEquals("COM002", json.readTree(audit.getContentAsString()).get("code").asText());
    }

    @Test
    void queryLocaleWinsAndUnsupportedExplicitLocaleIsRejected() throws Exception {
        var response = mvc.perform(get("/").param("lang", "vi").header("X-Locale", "en")).andReturn().getResponse();
        assertEquals("vi", response.getHeader("Content-Language"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("Quản lý nhà xe"));
        var invalid = mvc.perform(get("/").param("lang", "fr")).andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertEquals("COM003", json.readTree(invalid.getContentAsString()).get("code").asText());
    }
}
