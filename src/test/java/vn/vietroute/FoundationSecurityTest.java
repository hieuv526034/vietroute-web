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
import vn.vietroute.auth.dto.request.access.RolePermissionsRequest;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.business.catalog.service.CatalogService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.media.service.MediaService;
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

    @Test
    void creatingDoesNotGrantReadUpdateDeleteAndLegacyWriteDoesNotGrantDelete() {
        var catalogs = context.getBean(CatalogService.class);
        authenticate("CATALOG_CREATE");
        assertThrows(AccessDeniedException.class, () -> catalogs.find(1, Locale.ENGLISH));
        assertThrows(AccessDeniedException.class, () -> catalogs.update(1, null, null, Locale.ENGLISH));
        assertThrows(AccessDeniedException.class, () -> catalogs.delete(1, 0, null, Locale.ENGLISH));
        authenticate("CATALOG_UPDATE");
        assertThrows(AccessDeniedException.class, () -> catalogs.create(null, null, Locale.ENGLISH));
        authenticate("CATALOG_WRITE");
        assertThrows(AccessDeniedException.class, () -> catalogs.delete(1, 0, null, Locale.ENGLISH));
    }

    @Test
    void delegatedAssignmentCannotGrantPrivilegesTheActorDoesNotHave() {
        authenticate("ACCESS_ASSIGN");
        var body = new RolePermissionsRequest();
        body.setPermissions(Set.of(Permission.SYSTEM_READ));
        var actor = new UserPrincipal(2L, "operator", Set.of(Role.OPERATOR), 0);
        var error = assertThrows(BusinessException.class, () ->
            context.getBean(AccessService.class).replace(Role.CUSTOMER, body, actor)
        );
        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(2L, "testuser", Set.of(Role.OPERATOR), 0),
                null,
                List.of(new SimpleGrantedAuthority(authority))
            )
        );
    }

    @Test
    void mediaMethodSecurityDeniesUnrelatedPermissionsBeforeDatabaseOrProvider() {
        authenticate("CATALOG_CREATE");
        var actor = new UserPrincipal(2L, "operator", Set.of(Role.OPERATOR), 0);
        var service = context.getBean(MediaService.class);
        assertThrows(AccessDeniedException.class, () -> service.upload(null, actor));
        assertThrows(AccessDeniedException.class, () -> service.find(1, actor));
        assertThrows(AccessDeniedException.class, () -> service.delete(1, 0, actor));
        authenticate("MEDIA_CREATE");
        assertEquals(
            ErrorCode.MEDIA_DISABLED,
            assertThrows(BusinessException.class, () -> service.upload(null, actor)).getErrorCode()
        );
    }

    @Test
    void swaggerDescribesMultipartBinaryAndMediaResponse() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        JsonNode doc = json.readTree(response.getContentAsString());
        String ref = doc
            .at("/paths/~1api~1v1~1media/post/requestBody/content/multipart~1form-data/schema/$ref")
            .asText();
        assertEquals("#/components/schemas/MediaUploadRequest", ref);
        var file = doc.at("/components/schemas/MediaUploadRequest/properties/file");
        assertEquals("string", file.path("type").asText());
        assertEquals("binary", file.path("format").asText());
        assertFalse(doc.at("/components/schemas/MediaResponse/properties").has("publicId"));
    }

    @Test
    void mediaPageRendersVietnameseAndEnglishWithoutDatabase() throws Exception {
        var viewer = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(2L, "viewer", Set.of(Role.OPERATOR), 0),
            null,
            List.of(new SimpleGrantedAuthority("MEDIA_READ"), new SimpleGrantedAuthority("MEDIA_VIEW"))
        );
        var vi = mvc
            .perform(get("/media").param("lang", "vi").with(authentication(viewer)))
            .andReturn()
            .getResponse();
        var en = mvc
            .perform(get("/media").param("lang", "en").with(authentication(viewer)))
            .andReturn()
            .getResponse();
        assertEquals(200, vi.getStatus());
        assertEquals(200, en.getStatus());
        assertTrue(vi.getContentAsString(StandardCharsets.UTF_8).contains("Thư viện ảnh"));
        assertTrue(en.getContentAsString(StandardCharsets.UTF_8).contains("Image library"));
        assertTrue(vi.getContentAsString(StandardCharsets.UTF_8).contains("/assets/js/media.js"));
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
