package vn.vietroute.auth.security;

import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Chọn trang vào hệ thống từ principal đã xác thực; đường dẫn này không thay kiểm quyền backend. */
public final class AuthNavigation {

    private AuthNavigation() {}

    /** Khách đến login; tài khoản đã xác thực vào trang theo role, không mở DB chỉ để chọn URL. */
    public static String workspace(UserPrincipal user) {
        if (user == null) {
            return Routes.LOGIN;
        }
        return Routes.DASHBOARD;
    }
}
