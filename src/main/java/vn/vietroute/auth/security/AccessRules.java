package vn.vietroute.auth.security;

import vn.vietroute.common.constant.AccessConstants.Permissions;

/**
 * Guard service dùng cùng mã với annotation HTTP. hasAuthority đối chiếu authorities đã nạp từ DB mỗi request,
 * không cấp quyền từ tên hằng. Giữ guard này để lời gọi service nội bộ không bỏ qua kiểm tra ở controller.
 */
public final class AccessRules {

    private AccessRules() {}

    public static final String AUTHENTICATED = "isAuthenticated()";

    public static final String ADMIN = "hasRole('ADMIN')";

    public static final String CATALOG_READ = "hasAuthority('" + Permissions.CATALOG_READ + "')";

    public static final String CATALOG_DELETE = "hasAuthority('" + Permissions.CATALOG_DELETE + "')";

    public static final String ACCESS_READ = "hasAuthority('" + Permissions.ACCESS_READ + "')";

    public static final String ACCESS_DELETE = "hasAuthority('" + Permissions.ACCESS_DELETE + "')";

    public static final String ACCESS_ASSIGN = "hasAuthority('" + Permissions.ACCESS_ASSIGN + "')";

    public static final String JOB_READ = "hasAuthority('" + Permissions.JOB_READ + "')";

    public static final String JOB_RETRY = "hasAuthority('" + Permissions.JOB_RETRY + "')";

    public static final String MAIL_SEND = "hasAuthority('" + Permissions.MAIL_SEND + "')";

    public static final String SYSTEM_READ = "hasAuthority('" + Permissions.SYSTEM_READ + "')";

    public static final String MEDIA_READ = "hasAuthority('" + Permissions.MEDIA_READ + "')";

    public static final String MEDIA_CREATE = "hasAuthority('" + Permissions.MEDIA_CREATE + "')";

    public static final String MEDIA_UPDATE = "hasAuthority('" + Permissions.MEDIA_UPDATE + "')";

    public static final String MEDIA_DELETE = "hasAuthority('" + Permissions.MEDIA_DELETE + "')";

    public static final String CATALOG_VIEW = "hasAuthority('" + Permissions.CATALOG_VIEW + "')";

    public static final String MEDIA_VIEW = "hasAuthority('" + Permissions.MEDIA_VIEW + "')";

    public static final String ACCESS_VIEW = "hasAuthority('" + Permissions.ACCESS_VIEW + "')";

    public static final String JOB_VIEW = "hasAuthority('" + Permissions.JOB_VIEW + "')";

    public static final String SYSTEM_VIEW = "hasAuthority('" + Permissions.SYSTEM_VIEW + "')";

    public static final String CONFIGURATION_VIEW = "hasAuthority('" + Permissions.CONFIGURATION_VIEW + "')";

    public static final String CONFIGURATION_READ = "hasAuthority('" + Permissions.CONFIGURATION_READ + "')";

    public static final String JOURNAL_VIEW = "hasAuthority('" + Permissions.JOURNAL_VIEW + "')";

    public static final String CATALOG_CREATE =
        "hasAnyAuthority('" + Permissions.CATALOG_CREATE + "','" + Permissions.CATALOG_WRITE + "')";

    public static final String CATALOG_UPDATE =
        "hasAnyAuthority('" + Permissions.CATALOG_UPDATE + "','" + Permissions.CATALOG_WRITE + "')";

    public static final String ASSIGN =
        "hasAnyAuthority('" + Permissions.ACCESS_ASSIGN + "','" + Permissions.ACCESS_WRITE + "')";

    public static final String REGISTRY_SAVE =
        "((#create and hasAuthority('" +
        Permissions.ACCESS_CREATE +
        "')) or (!#create and hasAuthority('" +
        Permissions.ACCESS_UPDATE +
        "')))";

    public static final String POLICY_SAVE =
        "((#id == 0 and hasAuthority('" +
        Permissions.ACCESS_CREATE +
        "')) or (#id > 0 and hasAuthority('" +
        Permissions.ACCESS_UPDATE +
        "')))";

    /** Quản lý danh tính toàn hệ thống chỉ dành cho ADMIN; quyền gán role vẫn là hành động độc lập. */
    public static final String ACCOUNT_READ = ADMIN + " and hasAuthority('" + Permissions.ACCOUNT_READ + "')";
    public static final String ACCOUNT_CREATE =
        ADMIN + " and hasAuthority('" + Permissions.ACCOUNT_CREATE + "') and " + ACCESS_ASSIGN;
    public static final String ACCOUNT_UPDATE = ADMIN + " and hasAuthority('" + Permissions.ACCOUNT_UPDATE + "')";
    public static final String ACCOUNT_VIEW = ADMIN + " and hasAuthority('" + Permissions.ACCOUNT_VIEW + "')";

    public static final String SENSITIVE_READ = ADMIN + " and " + SYSTEM_READ;
}
