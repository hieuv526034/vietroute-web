package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeEndpointWebFixture;
import vn.vietroute.support.NativeSecurityFixture;

/** Thay policy bổ sung bằng grant endpoint duy nhất; page trực tiếp và API có quyền độc lập. */
class AccessPolicyTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pageAndApiRequireTheirOwnMappingsAndHeadUsesTheGetTemplate() throws Exception {
        try (var web = new NativeEndpointWebFixture()) {
            var state = new NativeSecurityFixture();
            state.state = state.ready(Set.of("CUSTOM"), Set.of("GET /api/v1/native-probe/{id}"));
            var guard = new EndpointAccess(null, state.repository, null);
            guard.collect(web.context.getBean(RequestMappingHandlerMapping.class));
            var handler = new HandlerMethod(
                new NativeEndpointWebFixture.ProbeAPI(),
                NativeEndpointWebFixture.ProbeAPI.class.getMethod("get")
            );
            SecurityContextHolder.getContext().setAuthentication(state.actor(7, "CUSTOM", 2));
            var request = new MockHttpServletRequest("HEAD", "/api/v1/native-probe/9");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/native-probe/{id}");
            assertTrue(guard.preHandle(request, new MockHttpServletResponse(), handler));
            request.setMethod("GET");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/operations/native-probe");
            assertThrows(BusinessException.class, () ->
                guard.preHandle(request, new MockHttpServletResponse(), handler)
            );
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/uncollected");
            assertThrows(BusinessException.class, () ->
                guard.preHandle(request, new MockHttpServletResponse(), handler)
            );
        }
    }

    @Test
    void publicEndpointDoesNotRequireDbOrAcceptClientPermissionMetadata() throws Exception {
        try (var web = new NativeEndpointWebFixture()) {
            var guard = new EndpointAccess(null, new NativeSecurityFixture().repository, null);
            var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/auth/csrf");
            request.addHeader("X-Role", "ADMIN");
            guard.collect(web.context.getBean(RequestMappingHandlerMapping.class));
            var handler = new HandlerMethod(
                new NativeEndpointWebFixture.ProbeAPI(),
                NativeEndpointWebFixture.ProbeAPI.class.getMethod("csrf")
            );
            assertTrue(guard.preHandle(request, new MockHttpServletResponse(), handler));
        }
    }
}
