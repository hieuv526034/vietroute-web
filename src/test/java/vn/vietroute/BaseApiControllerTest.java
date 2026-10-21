package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.common.base.BaseApiController;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.exception.GlobalExceptionHandler;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;
import vn.vietroute.common.web.RequestTraceFilter;
import vn.vietroute.common.web.SupportedLocaleResolver;

/** Kiểm tra hợp đồng HTTP thực tế của base và advice bằng controller test, không cần database. */
class BaseApiControllerTest {

    private MockMvc mvc;

    private ObjectMapper json;

    @BeforeEach
    void setup() {
        RootConfig configuration = new RootConfig();
        json = configuration.objectMapper();
        ApiResponses responses = new ApiResponses(
            configuration.messageSource(),
            new SupportedLocaleResolver(),
            Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)
        );
        ApiExceptionMapper exceptions = new ApiExceptionMapper(responses);
        mvc = MockMvcBuilders.standaloneSetup(new ExampleController(responses, exceptions))
            .setControllerAdvice(new GlobalExceptionHandler(exceptions))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .addFilters(new RequestTraceFilter())
            .build();
    }

    @Test
    void wrapsSuccessWithStableCodeAndInstant() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/test/success")).andReturn().getResponse();
        JsonNode body = body(response);
        assertEquals(200, response.getStatus());
        assertTrue(body.get("success").asBoolean());
        assertEquals("OK000", body.get("code").asText());
        assertEquals("done", body.get("data").asText());
        assertEquals("2026-10-21T10:00:00Z", body.get("timestamp").asText());
    }

    @Test
    void preservesBusinessCodeWithLocaleAndTraceId() throws Exception {
        MockHttpServletResponse vi = mvc
            .perform(get("/api/test/conflict").header("X-Locale", "vi").header("X-Request-ID", "base-api-request-123"))
            .andReturn()
            .getResponse();
        MockHttpServletResponse en = mvc
            .perform(get("/api/test/conflict").header("X-Locale", "en"))
            .andReturn()
            .getResponse();
        assertEquals(409, vi.getStatus());
        assertEquals(409, en.getStatus());
        assertEquals("COM005", body(vi).get("code").asText());
        assertEquals(body(vi).get("code"), body(en).get("code"));
        assertNotEquals(body(vi).get("message"), body(en).get("message"));
        assertEquals("base-api-request-123", body(vi).get("traceId").asText());
        assertEquals(body(vi).get("traceId").asText(), vi.getHeader("X-Request-ID"));
        assertEquals("no-store", vi.getHeader("Cache-Control"));
    }

    @Test
    void hidesInternalDetailsAndReturns500() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/test/unexpected")).andReturn().getResponse();
        assertEquals(500, response.getStatus());
        assertEquals("COM006", body(response).get("code").asText());
        assertFalse(body(response).get("success").asBoolean());
        assertTrue(body(response).get("data").isNull());
        assertFalse(response.getContentAsString().contains("internal-detail-never-return"));
    }

    @Test
    void preservesUnavailableAndDeniedStatuses() throws Exception {
        MockHttpServletResponse database = mvc.perform(get("/api/test/database")).andReturn().getResponse();
        MockHttpServletResponse denied = mvc.perform(get("/api/test/denied")).andReturn().getResponse();
        assertEquals(503, database.getStatus());
        assertEquals("COM007", body(database).get("code").asText());
        assertEquals(403, denied.getStatus());
        assertEquals("AUTH003", body(denied).get("code").asText());
    }

    @Test
    void catchesInvalidJsonBeforeControllerWithSameEnvelope() throws Exception {
        MockHttpServletResponse response = mvc
            .perform(post("/api/test").contentType("application/json").content("{invalid-json"))
            .andReturn()
            .getResponse();
        assertEquals(400, response.getStatus());
        assertEquals("COM002", body(response).get("code").asText());
        assertFalse(body(response).get("traceId").asText().isBlank());
    }

    @Test
    void createdAnnotationDoesNotOverwriteConflictResponse() throws Exception {
        MockHttpServletResponse created = mvc
            .perform(
                post("/api/test")
                    .contentType("application/json")
                    .content("{\"username\":\"success\",\"password\":\"test-only\"}")
            )
            .andReturn()
            .getResponse();
        MockHttpServletResponse conflict = mvc
            .perform(
                post("/api/test")
                    .contentType("application/json")
                    .content("{\"username\":\"conflict\",\"password\":\"test-only\"}")
            )
            .andReturn()
            .getResponse();
        assertEquals(201, created.getStatus());
        assertEquals(409, conflict.getStatus());
        assertEquals("COM005", body(conflict).get("code").asText());
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    @RestController
    @RequestMapping("/api/test")
    static class ExampleController extends BaseApiController {

        ExampleController(ApiResponses responses, ApiExceptionMapper exceptions) {
            super(responses, exceptions);
        }

        @GetMapping("/{scenario}")
        public ResponseEntity<ApiResponse<String>> read(@PathVariable String scenario, HttpServletRequest request) {
            return execute(SuccessCode.OK, request, () -> data(scenario));
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public ResponseEntity<ApiResponse<String>> create(@RequestBody LoginRequest body, HttpServletRequest request) {
            return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> data(body.getUsername()));
        }

        private String data(String scenario) {
            return switch (scenario) {
                case "conflict" -> throw new BusinessException(ErrorCode.CONFLICT);
                case "database" -> throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
                case "denied" -> throw new AccessDeniedException("test-only");
                case "unexpected" -> throw new IllegalStateException("internal-detail-never-return");
                default -> "done";
            };
        }
    }
}
