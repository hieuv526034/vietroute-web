package vn.vietroute.auth.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.response.AccessTargetResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Inventory từ mapping thật; route public, xác thực và quản trị quyền không nhận chính sách tùy biến. */
public final class AccessTargets {

    private AccessTargets() {}

    public static List<String> menus() {
        return List.of(
            Routes.ADMIN,
            Routes.ACCOUNT,
            Routes.MEDIA_PAGE,
            Routes.MONITORING_PAGE,
            Routes.JOBS_PAGE,
            Routes.CONFIGURATION_PAGE,
            Routes.API_JOURNAL_PAGE
        );
    }

    public static boolean configurable(String method, String path) {
        return (
            !path.startsWith(Routes.ACCESS) &&
            !path.startsWith(Routes.AUTH) &&
            !path.equals(Routes.ACCESS_PAGE) &&
            !path.equals(Routes.METRICS) &&
            !WebRoutePolicy.publicApi(path, method) &&
            (menus().contains(path) || path.startsWith(Routes.API))
        );
    }

    public static List<AccessTargetResponse> inventory(RequestMappingHandlerMapping mapping) {
        List<AccessTargetResponse> result = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) ->
            info.getPatternValues().forEach(path ->
                info
                    .getMethodsCondition()
                    .getMethods()
                    .forEach(method -> {
                        if (configurable(method.name(), path)) {
                            result.add(
                                new AccessTargetResponse(
                                    "ENDPOINT",
                                    method.name() + " " + path,
                                    handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()
                                )
                            );
                        }
                    })
            )
        );
        menus().forEach(path -> result.add(new AccessTargetResponse("MENU", "GET " + path, "Menu")));
        return result
            .stream()
            .sorted(Comparator.comparing(AccessTargetResponse::kind).thenComparing(AccessTargetResponse::target))
            .toList();
    }
}
