package vn.vietroute.auth.entity;

import java.util.Set;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Quyền là hành động cụ thể; role chỉ gom quyền, không được dùng role để bỏ qua phạm vi dữ liệu. */
@JdbcTable(
    name = "auth_permissions",
    primaryKey = { "code" },
    indexes = { @JdbcIndex(name = "auth_permissions_pkey", columns = { "code" }, unique = true) }
)
public enum Permission {
    CATALOG_READ("CATALOG", Set.of(PermissionAction.READ)),
    CATALOG_CREATE("CATALOG", Set.of(PermissionAction.CREATE)),
    CATALOG_UPDATE("CATALOG", Set.of(PermissionAction.UPDATE)),
    CATALOG_DELETE("CATALOG", Set.of(PermissionAction.DELETE)),
    // Giữ mã cũ để không làm mất quyền role đang dùng: WRITE chỉ gồm tạo/sửa, không ngầm được đọc/xóa/gán.
    CATALOG_WRITE("CATALOG", Set.of(PermissionAction.CREATE, PermissionAction.UPDATE)),
    ACCESS_READ("ACCESS", Set.of(PermissionAction.READ)),
    ACCESS_ASSIGN("ACCESS", Set.of(PermissionAction.ASSIGN)),
    // ACCESS_WRITE là mã tương thích cũ cho thao tác gán; API mới hướng dẫn dùng ACCESS_ASSIGN.
    ACCESS_WRITE("ACCESS", Set.of(PermissionAction.ASSIGN)),
    JOB_READ("JOB", Set.of(PermissionAction.READ)),
    JOB_RETRY("JOB", Set.of(PermissionAction.RETRY)),
    MAIL_SEND("MAIL", Set.of(PermissionAction.SEND)),
    SYSTEM_READ("SYSTEM", Set.of(PermissionAction.READ)),
    MEDIA_READ("MEDIA", Set.of(PermissionAction.READ)),
    MEDIA_CREATE("MEDIA", Set.of(PermissionAction.CREATE)),
    MEDIA_DELETE("MEDIA", Set.of(PermissionAction.DELETE));

    private final String resource;

    private final Set<PermissionAction> actions;

    Permission(String resource, Set<PermissionAction> actions) {
        this.resource = resource;
        this.actions = Set.copyOf(actions);
    }

    public String resource() {
        return resource;
    }

    public Set<PermissionAction> actions() {
        return actions;
    }

    public String messageKey() {
        return "permission." + name();
    }

    public boolean grantedInitially(Role role) {
        return role == Role.ADMIN;
    }
}
