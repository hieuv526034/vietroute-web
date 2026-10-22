package vn.vietroute.common.web.routing;

import java.util.List;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/**
 * Chính sách route HTTP dùng chung: giữ riêng public, bỏ qua JWT và miễn quota vì mục đích khác nhau.
 * Route mới mặc định cần xác thực; khai báo đường dẫn trong constants không tự cấp quyền truy cập.
 */
public final class WebRoutePolicy {

    private WebRoutePolicy() {}

    /** Matcher permitAll của Security; catalog chỉ công khai cho GET ở cấu hình riêng. */
    public static List<String> publicMatchers() {
        return List.of(
            Routes.HOME,
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
            path.startsWith(Routes.DOCS) ||
            path.equals(Routes.OPENAPI)
        );
    }

    /** Cùng điều kiện cho Swagger mô tả API công khai; POST catalog luôn cần quyền ở service. */
    public static boolean publicApi(String path, String method) {
        return (
            path.equals(Routes.AUTH_LOGIN) ||
            path.equals(Routes.AUTH_CSRF) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            publicCatalog(path, method)
        );
    }

    /** Cookie hết hạn được xóa để người dùng mở trang công khai; Bearer sai vẫn bị từ chối. */
    public static boolean recoversInvalidCookie(String path, String method) {
        return (
            path.equals(Routes.HOME) ||
            path.equals(Routes.LOGIN) ||
            path.equals(Routes.AUTH_LOGOUT) ||
            publicCatalog(path, method)
        );
    }

    private static boolean publicCatalog(String path, String method) {
        return method.equals("GET") && path.startsWith(Routes.CATALOG_PREFIX);
    }
}
