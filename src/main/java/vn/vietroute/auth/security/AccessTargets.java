package vn.vietroute.auth.security;

import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.AccessTargetResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Mapping Spring là nguồn đường dẫn; danh bạ DB chỉ ghi nhận, không trở thành nguồn permitAll. */
public final class AccessTargets {

    private static final AntPathMatcher PUBLIC_MATCHER = new AntPathMatcher();

    private AccessTargets() {}

    /** Auth/quản trị quyền/metrics là đường cứu hộ; UI và API còn lại chỉ được siết thêm quyền. */
    public static boolean configurable(String method, String path) {
        return (
            !path.startsWith(Routes.ACCESS) &&
            !path.startsWith(Routes.AUTH) &&
            !path.equals(Routes.ACCESS_PAGE) &&
            !path.equals(Routes.METRICS) &&
            !publicRoute(method, path) &&
            (method.equals("GET") || path.startsWith(Routes.API))
        );
    }

    /** Chỉ các route Security thực sự permitAll; không suy ra công khai từ annotation hay dữ liệu DB. */
    private static boolean publicRoute(String method, String path) {
        return (
            WebRoutePolicy.publicApi(path, method) ||
            WebRoutePolicy.publicMatchers()
                .stream()
                .anyMatch(pattern -> PUBLIC_MATCHER.match(pattern, path))
        );
    }

    /** Tự thu thập cả trang MVC và API; method/path/template lấy trực tiếp từ RequestMappingHandlerMapping. */
    public static List<AccessResourceResponse> discover(RequestMappingHandlerMapping mapping) {
        List<AccessResourceResponse> result = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (info.getMethodsCondition().isEmpty()) {
                throw new IllegalStateException(
                    "Controller phải khai báo HTTP method cụ thể: " + handler.getShortLogMessage()
                );
            }
            String kind = AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class)
                ? "ENDPOINT"
                : "MENU";
            Operation operation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), Operation.class);
            String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
            String description = operation == null || operation.summary().isBlank() ? name : operation.summary();
            info.getPatternValues().forEach(path ->
                info
                    .getMethodsCondition()
                    .getMethods()
                    .forEach(method -> {
                        result.add(
                            new AccessResourceResponse(
                                kind,
                                method.name() + " " + path,
                                name,
                                description,
                                publicRoute(method.name(), path),
                                configurable(method.name(), path),
                                null,
                                null
                            )
                        );
                    })
            );
        });
        return result
            .stream()
            .sorted(Comparator.comparing(AccessResourceResponse::kind).thenComparing(AccessResourceResponse::target))
            .toList();
    }

    /** Picker chỉ dùng mapping đang có trên node xử lý; route cũ trong DB không tự thành policy hợp lệ. */
    public static List<AccessTargetResponse> inventory(RequestMappingHandlerMapping mapping) {
        return discover(mapping)
            .stream()
            .filter(AccessResourceResponse::configurable)
            .map(resource -> new AccessTargetResponse(resource.kind(), resource.target(), resource.handler()))
            .toList();
    }
}
