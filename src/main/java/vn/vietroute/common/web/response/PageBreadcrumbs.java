package vn.vietroute.common.web.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import vn.vietroute.auth.dto.response.NavigationItemResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Breadcrumb từ cây menu đã lọc quyền; không dựng URL từ query hoặc cho nhóm trỏ tới trang bị cấm. */
public final class PageBreadcrumbs {

    private PageBreadcrumbs() {}

    /** Mỗi mục có link trong ứng dụng; nhóm không có trang chọn trang con đầu tiên mà người dùng được mở. */
    public static List<NavigationItemResponse> build(
        List<NavigationItemResponse> menu,
        String path,
        Locale locale,
        MessageSource messages
    ) {
        if (Routes.HOME.equals(path)) return List.of();
        var result = new ArrayList<NavigationItemResponse>();
        result.add(item(messages.getMessage("ui.home", null, locale), Routes.HOME, false));
        String canonical = Routes.LOCATION_PAGE.equals(path) ? Routes.CATALOG_LOCATIONS_PAGE : path;
        if (append(menu, canonical, result)) return List.copyOf(result);
        String key = switch (path) {
            case Routes.ACCOUNT -> "ui.account";
            case Routes.LOGIN -> "ui.login";
            case Routes.AUTH_LIFECYCLE_PAGE -> "ui.accountFlow.title";
            case Routes.PUBLIC_CARRIERS_PAGE -> "ui.publicCarrier.title";
            case Routes.JOURNEYS_PAGE -> "ui.journey.search";
            case Routes.DOCS -> "ui.nav.swagger";
            default -> null;
        };
        if (key != null) result.add(item(messages.getMessage(key, null, locale), path, true));
        return result.size() > 1 ? List.copyOf(result) : List.of();
    }

    private static boolean append(List<NavigationItemResponse> nodes, String path, List<NavigationItemResponse> trail) {
        for (var node : nodes) {
            if (path.equals(node.path())) {
                trail.add(item(node.name(), path, true));
                return true;
            }
            var nested = new ArrayList<NavigationItemResponse>();
            if (append(node.children(), path, nested)) {
                trail.add(item(node.name(), node.path() != null ? node.path() : firstPath(node.children()), false));
                trail.addAll(nested);
                return true;
            }
        }
        return false;
    }

    private static String firstPath(List<NavigationItemResponse> nodes) {
        for (var node : nodes) {
            if (node.path() != null) return node.path();
            String child = firstPath(node.children());
            if (child != null) return child;
        }
        return null;
    }

    private static NavigationItemResponse item(String name, String path, boolean active) {
        return new NavigationItemResponse(name, path, "", active, List.of());
    }
}
