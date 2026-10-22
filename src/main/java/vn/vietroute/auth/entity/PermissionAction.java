package vn.vietroute.auth.entity;

/** Hành động chuẩn để UI nhóm quyền; luôn đi cùng resource, không có quyền READ/DELETE toàn hệ thống. */
public enum PermissionAction {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    ASSIGN,
    RETRY,
    SEND;

    public String messageKey() {
        return "permissionAction." + name();
    }
}
