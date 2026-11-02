package vn.vietroute.support;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.security.EndpointAccess;

/** Spring MVC thật, không khai báo bất kỳ annotation quyền hoặc registry function nào. */
public class NativeEndpointWebFixture implements AutoCloseable {

    public final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    public final EndpointAccess catalog = new EndpointAccess(null, null, null);

    public NativeEndpointWebFixture(Class<?>... extra) {
        context.setServletContext(new MockServletContext());
        context.register(Config.class, ProbeAPI.class, ProbePageController.class);
        if (extra.length > 0) context.register(extra);
        context.refresh();
        catalog.collect(context.getBean(RequestMappingHandlerMapping.class));
    }

    @Override
    public void close() {
        context.close();
    }

    @Configuration
    @EnableWebMvc
    public static class Config {}

    @RestController
    public static class ProbeAPI {

        @GetMapping("/api/v1/native-probe/{id}")
        @Operation(summary = "Chi tiết tự thu thập")
        public String get() {
            return "ok";
        }

        @PutMapping("/api/v1/native-probe/{id}")
        @Operation(summary = "Sửa tự thu thập")
        public String update() {
            return "ok";
        }

        @GetMapping("/api/v1/auth/csrf")
        @Operation(summary = "CSRF công khai")
        public String csrf() {
            return "ok";
        }

        @GetMapping("/api/v1/auth/me")
        @Operation(summary = "Danh tính tự phục vụ")
        public String me() {
            return "ok";
        }
    }

    @Controller
    public static class ProbePageController {

        @GetMapping("/operations/native-probe")
        public String page() {
            return "unused";
        }
    }

    @RestController
    public static class InvalidAPI {

        @RequestMapping("/api/v1/missing-method")
        public String invalid() {
            return "invalid";
        }
    }
}
