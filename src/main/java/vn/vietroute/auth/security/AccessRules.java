package vn.vietroute.auth.security;

import vn.vietroute.common.constant.AccessConstants.Permissions;

/**
 * Guard service gọi accessGuard đọc mapping hiện hành trong DB; chuỗi code chỉ khai báo quyền cần kiểm.
 * Không tin authorities cũ hoặc gán quyền bằng hằng. Method security bảo vệ cả lời gọi bean nội bộ.
 */
public final class AccessRules {

    private AccessRules() {}

    public static final String AUTHENTICATED = "@accessGuard.authenticated(authentication)";

    public static final String ADMIN = "@accessGuard.admin(authentication)";

    public static final String CATALOG_READ = "@accessGuard.has(authentication,'" + Permissions.CATALOG_READ + "')";

    public static final String CATALOG_DELETE = "@accessGuard.has(authentication,'" + Permissions.CATALOG_DELETE + "')";

    public static final String ACCESS_READ = "@accessGuard.has(authentication,'" + Permissions.ACCESS_READ + "')";

    public static final String ACCESS_DELETE = "@accessGuard.has(authentication,'" + Permissions.ACCESS_DELETE + "')";

    public static final String ACCESS_ASSIGN = "@accessGuard.has(authentication,'" + Permissions.ACCESS_ASSIGN + "')";

    public static final String JOB_READ = "@accessGuard.has(authentication,'" + Permissions.JOB_READ + "')";

    public static final String JOB_RETRY = "@accessGuard.has(authentication,'" + Permissions.JOB_RETRY + "')";

    public static final String MAIL_SEND = "@accessGuard.has(authentication,'" + Permissions.MAIL_SEND + "')";

    public static final String SYSTEM_READ = "@accessGuard.has(authentication,'" + Permissions.SYSTEM_READ + "')";

    public static final String MEDIA_READ = "@accessGuard.has(authentication,'" + Permissions.MEDIA_READ + "')";

    public static final String MEDIA_CREATE = "@accessGuard.has(authentication,'" + Permissions.MEDIA_CREATE + "')";

    public static final String MEDIA_UPDATE = "@accessGuard.has(authentication,'" + Permissions.MEDIA_UPDATE + "')";

    public static final String MEDIA_DELETE = "@accessGuard.has(authentication,'" + Permissions.MEDIA_DELETE + "')";

    public static final String CATALOG_VIEW = "@accessGuard.has(authentication,'" + Permissions.CATALOG_VIEW + "')";

    public static final String MEDIA_VIEW = "@accessGuard.has(authentication,'" + Permissions.MEDIA_VIEW + "')";

    public static final String ACCESS_VIEW = "@accessGuard.has(authentication,'" + Permissions.ACCESS_VIEW + "')";

    public static final String JOB_VIEW = "@accessGuard.has(authentication,'" + Permissions.JOB_VIEW + "')";

    public static final String SYSTEM_VIEW = "@accessGuard.has(authentication,'" + Permissions.SYSTEM_VIEW + "')";

    public static final String CONFIGURATION_VIEW =
        "@accessGuard.has(authentication,'" + Permissions.CONFIGURATION_VIEW + "')";

    public static final String CONFIGURATION_READ =
        "@accessGuard.has(authentication,'" + Permissions.CONFIGURATION_READ + "')";

    public static final String JOURNAL_VIEW = "@accessGuard.has(authentication,'" + Permissions.JOURNAL_VIEW + "')";

    public static final String CATALOG_CREATE =
        "@accessGuard.any(authentication,'" + Permissions.CATALOG_CREATE + "','" + Permissions.CATALOG_WRITE + "')";

    public static final String CATALOG_UPDATE =
        "@accessGuard.any(authentication,'" + Permissions.CATALOG_UPDATE + "','" + Permissions.CATALOG_WRITE + "')";

    public static final String ASSIGN =
        "@accessGuard.any(authentication,'" + Permissions.ACCESS_ASSIGN + "','" + Permissions.ACCESS_WRITE + "')";

    public static final String REGISTRY_SAVE =
        "@accessGuard.has(authentication, #create ? '" +
        Permissions.ACCESS_CREATE +
        "' : '" +
        Permissions.ACCESS_UPDATE +
        "')";

    public static final String POLICY_SAVE =
        "#id >= 0 and @accessGuard.has(authentication, #id == 0 ? '" +
        Permissions.ACCESS_CREATE +
        "' : '" +
        Permissions.ACCESS_UPDATE +
        "')";

    /** Quản lý danh tính toàn hệ thống chỉ dành cho ADMIN; quyền gán role vẫn là hành động độc lập. */
    public static final String ACCOUNT_READ =
        "@accessGuard.adminAll(authentication, '" + Permissions.ACCOUNT_READ + "')";

    public static final String ACCOUNT_CREATE =
        "@accessGuard.adminAll(authentication, '" +
        Permissions.ACCOUNT_CREATE +
        "','" +
        Permissions.ACCESS_ASSIGN +
        "')";

    public static final String ACCOUNT_UPDATE =
        "@accessGuard.adminAll(authentication, '" + Permissions.ACCOUNT_UPDATE + "')";

    public static final String ACCOUNT_VIEW =
        "@accessGuard.adminAll(authentication, '" + Permissions.ACCOUNT_VIEW + "')";

    public static final String SENSITIVE_READ =
        "@accessGuard.adminAll(authentication, '" + Permissions.SYSTEM_READ + "')";

    public static final String SENSITIVE_VIEW =
        "@accessGuard.adminAll(authentication, '" + Permissions.JOURNAL_VIEW + "')";
}
