package vn.vietroute.common.web.routing;

import java.util.List;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/**
 * Chính sách route HTTP dùng chung: giữ riêng public, bỏ qua JWT và miễn quota vì mục đích khác nhau.
 * Route mới mặc định cần xác thực; khai báo đường dẫn trong constants không tự cấp quyền truy cập.
 */
public final class WebRoutePolicy {

    private WebRoutePolicy() {}

    /** Matcher permitAll của Security; các danh mục tra cứu chỉ công khai cho GET ở cấu hình riêng. */
    public static List<String> publicMatchers() {
        return List.of(
            Routes.HOME,
            Routes.PUBLIC_CARRIERS_PAGE,
            Routes.HEALTH,
            Routes.LOGIN,
            Routes.AUTH_LIFECYCLE_PAGE,
            Routes.AUTH + Routes.AUTH_OPTIONS,
            Routes.AUTH + Routes.AUTH_REGISTER,
            Routes.AUTH + Routes.AUTH_ACTIVATE,
            Routes.AUTH + Routes.AUTH_RESEND,
            Routes.AUTH + Routes.AUTH_FORGOT,
            Routes.AUTH + Routes.AUTH_RESET,
            Routes.ASSET_MATCHER,
            Routes.WEBJAR_MATCHER,
            Routes.DOCS,
            Routes.DOCS_MATCHER,
            Routes.OPENAPI,
            Routes.AUTH_LOGIN,
            Routes.AUTH_CSRF,
            Routes.AUTH_LOGOUT,
            Routes.PRESENCE
        );
    }

    /** Health/static không dùng DB/JWT và không bị quota ứng dụng chặn probe hoặc asset. */
    public static boolean skipsTrafficGuard(String path) {
        return path.equals(Routes.HEALTH) || path.startsWith(Routes.ASSETS) || path.startsWith(Routes.WEBJARS);
    }

    /** Trang docs/login API không cần giải mã token cookie cũ; không đồng nghĩa miễn Security cho mọi route. */
    public static boolean skipsJwt(String path) {
        return (
            skipsTrafficGuard(path) ||
            path.equals(Routes.AUTH_LOGIN) ||
            path.equals(Routes.AUTH_CSRF) ||
            publicAccountFlow(path) ||
            path.equals(Routes.OPENAPI)
        );
    }

    /** Cùng điều kiện cho Swagger mô tả API công khai; thao tác ghi danh mục luôn cần quyền ở service. */
    public static boolean publicApi(String path, String method) {
        return (
            path.equals(Routes.AUTH_LOGIN) ||
            path.equals(Routes.AUTH_CSRF) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            path.equals(Routes.PRESENCE) ||
            publicAccountFlow(path) ||
            publicLookup(path, method)
        );
    }

    /** Cookie hết hạn được xóa để người dùng mở trang công khai; Bearer sai vẫn bị từ chối. */
    public static boolean recoversInvalidCookie(String path, String method) {
        return (
            path.equals(Routes.HOME) ||
            path.equals(Routes.PUBLIC_CARRIERS_PAGE) ||
            path.equals(Routes.LOGIN) ||
            path.equals(Routes.DOCS) ||
            path.equals(Routes.AUTH_LIFECYCLE_PAGE) ||
            publicAccountFlow(path) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            path.equals(Routes.PRESENCE) ||
            publicLookup(path, method)
        );
    }

    public static List<String> publicLookupMatchers() {
        return List.of(
            Routes.CATALOG_MATCHER,
            Routes.PROVINCES,
            Routes.LOCATIONS,
            Routes.PUBLIC_CARRIERS,
            Routes.PUBLIC_CARRIERS_MATCHER
        );
    }

    private static boolean publicLookup(String path, String method) {
        return (
            method.equals("GET") &&
            (path.startsWith(Routes.CATALOG_PREFIX) ||
                path.equals(Routes.PROVINCES) ||
                path.equals(Routes.LOCATIONS) ||
                path.equals(Routes.PUBLIC_CARRIERS) ||
                path.startsWith(Routes.PUBLIC_CARRIERS + "/"))
        );
    }

    /** Danh sách tường minh; email của user đang đăng nhập không thuộc nhóm public này. */
    private static boolean publicAccountFlow(String path) {
        return List.of(
            Routes.AUTH_OPTIONS,
            Routes.AUTH_REGISTER,
            Routes.AUTH_ACTIVATE,
            Routes.AUTH_RESEND,
            Routes.AUTH_FORGOT,
            Routes.AUTH_RESET
        )
            .stream()
            .anyMatch(action -> path.equals(Routes.AUTH + action));
    }

    /** Các mutation dùng password/mã email cùng chịu quota và semaphore login để hạn chế spam/BCrypt CPU. */
    public static boolean credentialMutation(String path, String method) {
        return (
            method.equals("POST") &&
            (path.equals(Routes.AUTH_LOGIN) ||
                path.equals(Routes.AUTH + Routes.AUTH_ADMIN_RECOVERY) ||
                path.equals(Routes.AUTH + Routes.AUTH_EMAIL) ||
                path.equals(Routes.AUTH + Routes.PASSWORD_ACTION) ||
                path.equals(Routes.ACCOUNTS) ||
                publicAccountFlow(path))
        );
    }
}
