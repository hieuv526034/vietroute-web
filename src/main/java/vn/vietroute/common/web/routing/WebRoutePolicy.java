package vn.vietroute.common.web.routing;

import java.util.List;
import org.springframework.util.AntPathMatcher;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/**
 * Chính sách route HTTP dùng chung: giữ riêng public, bỏ qua JWT và phân loại endpoint thiết lập tài khoản.
 * Route mới mặc định cần xác thực; khai báo đường dẫn trong constants không tự cấp quyền truy cập.
 */
public final class WebRoutePolicy {

    private WebRoutePolicy() {}

    /** Public/self-service là hợp đồng xác thực của ứng dụng, không phải role hoặc authority được viết cứng. */
    public static boolean publicEndpoint(String path, String method) {
        String verb = method.equals("HEAD") ? "GET" : method;
        AntPathMatcher matcher = new AntPathMatcher();
        return (
            publicApi(path, verb) ||
            publicMatchers()
                .stream()
                .anyMatch(pattern -> matcher.match(pattern, path))
        );
    }

    /** Các luồng thiết lập chính tài khoản phải còn dùng được trước khi có quyền nghiệp vụ. */
    public static boolean selfService(String path, String method) {
        String target = (method.equals("HEAD") ? "GET" : method) + " " + path;
        return List.of(
            "GET " + Routes.ACCOUNT,
            "GET " + Routes.DASHBOARD,
            "GET " + Routes.AUTH + Routes.ME_ACTION,
            "POST " + Routes.AUTH + Routes.PASSWORD_ACTION,
            "GET " + Routes.AUTH + Routes.AUTH_CAPABILITIES,
            "PUT " + Routes.AUTH + Routes.AUTH_PROFILE,
            "GET " + Routes.AUTH + Routes.ACCESS_SCOPES,
            "GET " + Routes.AUTH + Routes.ACCESS_SCOPE_PERMISSIONS
        ).contains(target);
    }

    /** Matcher permitAll của Security; các danh mục tra cứu chỉ công khai cho GET ở cấu hình riêng. */
    public static List<String> publicMatchers() {
        return List.of(
            Routes.HOME,
            Routes.PUBLIC_CARRIERS_PAGE,
            Routes.JOURNEYS_PAGE,
            Routes.HEALTH,
            Routes.LOGIN,
            Routes.ASSET_MATCHER,
            Routes.WEBJAR_MATCHER,
            Routes.DOCS,
            Routes.DOCS_MATCHER,
            Routes.OPENAPI,
            Routes.AUTH_LOGIN,
            Routes.AUTH_CSRF,
            Routes.AUTH_LOGOUT
        );
    }

    /** Health/static không dùng DB/JWT và không cần giải mã JWT cho probe hoặc asset. */
    public static boolean skipsTrafficGuard(String path) {
        return path.equals(Routes.HEALTH) || path.startsWith(Routes.ASSETS) || path.startsWith(Routes.WEBJARS);
    }

    /** Trang docs/login API không cần giải mã token cookie cũ; không đồng nghĩa miễn Security cho mọi route. */
    public static boolean skipsJwt(String path) {
        return (
            skipsTrafficGuard(path) ||
            path.equals(Routes.AUTH_LOGIN) ||
            path.equals(Routes.AUTH_CSRF) ||
            path.equals(Routes.OPENAPI)
        );
    }

    /** Cùng điều kiện cho Swagger mô tả API công khai; thao tác ghi danh mục luôn cần quyền ở service. */
    public static boolean publicApi(String path, String method) {
        return (
            path.equals(Routes.AUTH_LOGIN) ||
            path.equals(Routes.AUTH_CSRF) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            publicLookup(path, method)
        );
    }

    /** Cookie hết hạn được xóa để người dùng mở trang công khai; Bearer sai vẫn bị từ chối. */
    public static boolean recoversInvalidCookie(String path, String method) {
        return (
            path.equals(Routes.HOME) ||
            path.equals(Routes.PUBLIC_CARRIERS_PAGE) ||
            path.equals(Routes.JOURNEYS_PAGE) ||
            path.equals(Routes.LOGIN) ||
            path.equals(Routes.DOCS) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            publicLookup(path, method)
        );
    }

    public static List<String> publicLookupMatchers() {
        return List.of(
            Routes.CATALOG_MATCHER,
            Routes.PROVINCES,
            Routes.LOCATIONS,
            Routes.PUBLIC_CARRIERS,
            Routes.PUBLIC_CARRIERS_MATCHER,
            Routes.PUBLIC_TRIPS,
            Routes.PUBLIC_TRIPS_MATCHER
        );
    }

    private static boolean publicLookup(String path, String method) {
        return (
            method.equals("GET") &&
            (path.startsWith(Routes.CATALOG_PREFIX) ||
                path.equals(Routes.PROVINCES) ||
                path.equals(Routes.LOCATIONS) ||
                path.equals(Routes.PUBLIC_CARRIERS) ||
                path.startsWith(Routes.PUBLIC_CARRIERS + "/") ||
                path.equals(Routes.PUBLIC_TRIPS) ||
                path.startsWith(Routes.PUBLIC_TRIPS + "/"))
        );
    }

    /** Các mutation dùng BCrypt cùng chịu semaphore login để hạn chế spam/BCrypt CPU. */
    public static boolean credentialMutation(String path, String method) {
        return (
            method.equals("POST") &&
            (path.equals(Routes.AUTH_LOGIN) ||
                path.equals(Routes.AUTH + Routes.PASSWORD_ACTION) ||
                path.equals(Routes.ACCOUNTS))
        );
    }
}
