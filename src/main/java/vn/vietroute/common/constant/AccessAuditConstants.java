package vn.vietroute.common.constant;

/** Loại sự kiện của lịch sử quản trị, không phải danh sách authority hoặc một tầng quyền khác. */
public final class AccessAuditConstants {

    private AccessAuditConstants() {}

    public static final long CLEANUP_LOCK = 781205620L;

    public enum Event {
        ROLE_PERMISSIONS,
        ROLE_RESOURCES,
        USER_ROLES,
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_DELETED,
        PERMISSION_CREATED,
        PERMISSION_UPDATED,
        POLICY_CREATED,
        POLICY_UPDATED,
        POLICY_DELETED,
        ACCOUNT_CREATED,
        ACCOUNT_STATUS,
        ACCOUNT_UPDATED,
        ACCOUNT_DELETED,
        ADMIN_RECOVERED,
        SCOPE_CREATED,
        SCOPE_UPDATED,
        SCOPE_MEMBERSHIP;

        public String messageKey() {
            return "access.change." + name();
        }
    }

    public enum Target {
        ROLE,
        PERMISSION,
        USER,
        POLICY,
        SCOPE,
        MEMBERSHIP,
    }
}
