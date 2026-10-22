package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.business.catalog.service.CatalogService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.WebConfig;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

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
        context.register(RootConfig.class, WebConfig.class);
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

    @Test
    void roleAloneDoesNotBypassFunctionPermissionAtServiceBoundary() {
        authenticate("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () ->
            context.getBean(CatalogService.class).create(null, null, Locale.ENGLISH)
        );
        assertThrows(AccessDeniedException.class, () -> context.getBean(AccessService.class).roles());
    }

    @Test
    void permissionRegistryIsLocalizedWithoutDatabase() {
        authenticate("ACCESS_READ");
        var access = context.getBean(AccessService.class);
        assertNotEquals(
            access.permissions(Locale.ENGLISH).get(0).name(),
            access.permissions(Locale.forLanguageTag("vi")).get(0).name()
        );
    }

    @Test
    void administrativeRecoveryRoleCannotBeModified() {
        authenticate("ACCESS_WRITE");
        var error = assertThrows(BusinessException.class, () ->
            context.getBean(AccessService.class).replace(Role.ADMIN, new RolePermissionsRequest(), null)
        );
        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "test-user",
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
        var invalid = mvc.perform(get("/v3/api-docs").param("lang", "fr")).andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertEquals("COM003", json.readTree(invalid.getContentAsString()).get("code").asText());
    }
}
