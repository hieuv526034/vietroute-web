package vn.vietroute.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Quyền là hành động cụ thể; role chỉ gom quyền, không được dùng role để bỏ qua phạm vi dữ liệu. */
@JdbcTable(
    name = "auth_permissions",
    primaryKey = { "code" },
    indexes = {
        @JdbcIndex(name = "auth_permissions_pkey", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_permissions_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_permissions_updated_by", columns = { "updated_by" }),
    }
)
@Schema(type = "string", description = "Mã Permission ổn định từ registry, JSON dạng chuỗi")
public final class Permission {

    public static final Permission CATALOG_READ = new Permission(
        "CATALOG_READ",
        "CATALOG",
        Set.of(PermissionAction.READ)
    );
    public static final Permission CATALOG_CREATE = new Permission(
        "CATALOG_CREATE",
        "CATALOG",
        Set.of(PermissionAction.CREATE)
    );
    public static final Permission CATALOG_UPDATE = new Permission(
        "CATALOG_UPDATE",
        "CATALOG",
        Set.of(PermissionAction.UPDATE)
    );
    public static final Permission CATALOG_DELETE = new Permission(
        "CATALOG_DELETE",
        "CATALOG",
        Set.of(PermissionAction.DELETE)
    );
    // Giữ mã cũ để không làm mất quyền role đang dùng: WRITE chỉ gồm tạo/sửa, không ngầm được đọc/xóa/gán.
    public static final Permission CATALOG_WRITE = new Permission(
        "CATALOG_WRITE",
        "CATALOG",
        Set.of(PermissionAction.CREATE, PermissionAction.UPDATE)
    );
    public static final Permission ACCESS_READ = new Permission("ACCESS_READ", "ACCESS", Set.of(PermissionAction.READ));

    public static final Permission ACCESS_ASSIGN = new Permission(
        "ACCESS_ASSIGN",
        "ACCESS",
        Set.of(PermissionAction.ASSIGN)
    );
    // ACCESS_WRITE là mã tương thích cũ cho thao tác gán; API mới hướng dẫn dùng ACCESS_ASSIGN.
    public static final Permission ACCESS_WRITE = new Permission(
        "ACCESS_WRITE",
        "ACCESS",
        Set.of(PermissionAction.ASSIGN)
    );
    public static final Permission JOB_READ = new Permission("JOB_READ", "JOB", Set.of(PermissionAction.READ));

    public static final Permission JOB_RETRY = new Permission("JOB_RETRY", "JOB", Set.of(PermissionAction.RETRY));

    public static final Permission MAIL_SEND = new Permission("MAIL_SEND", "MAIL", Set.of(PermissionAction.SEND));

    public static final Permission SYSTEM_READ = new Permission("SYSTEM_READ", "SYSTEM", Set.of(PermissionAction.READ));

    public static final Permission MEDIA_READ = new Permission("MEDIA_READ", "MEDIA", Set.of(PermissionAction.READ));

    public static final Permission MEDIA_CREATE = new Permission(
        "MEDIA_CREATE",
        "MEDIA",
        Set.of(PermissionAction.CREATE)
    );
    public static final Permission MEDIA_DELETE = new Permission(
        "MEDIA_DELETE",
        "MEDIA",
        Set.of(PermissionAction.DELETE)
    );
    public static final Permission MEDIA_UPDATE = new Permission(
        "MEDIA_UPDATE",
        "MEDIA",
        Set.of(PermissionAction.UPDATE)
    );
    public static final Permission ACCESS_CREATE = new Permission(
        "ACCESS_CREATE",
        "ACCESS",
        Set.of(PermissionAction.CREATE)
    );
    public static final Permission ACCESS_UPDATE = new Permission(
        "ACCESS_UPDATE",
        "ACCESS",
        Set.of(PermissionAction.UPDATE)
    );
    public static final Permission ACCESS_DELETE = new Permission(
        "ACCESS_DELETE",
        "ACCESS",
        Set.of(PermissionAction.DELETE)
    );
    private final String code;

    private final String resource;

    private final Set<PermissionAction> actions;

    private Permission(String code, String resource, Set<PermissionAction> actions) {
        this.code = code;
        this.resource = resource;
        this.actions = Set.copyOf(actions);
    }

    @JsonValue
    public String name() {
        return code;
    }

    public String resource() {
        return resource;
    }

    public Set<PermissionAction> actions() {
        return actions;
    }

    public String messageKey() {
        for (Permission builtIn : values()) {
            if (builtIn.equals(this)) {
                return "permission." + code;
            }
        }
        return "permission.custom";
    }

    public boolean grantedInitially(Role role) {
        return Role.ADMIN.equals(role);
    }

    public static Permission[] values() {
        return new Permission[] {
            CATALOG_READ,
            CATALOG_CREATE,
            CATALOG_UPDATE,
            CATALOG_DELETE,
            CATALOG_WRITE,
            ACCESS_READ,
            ACCESS_ASSIGN,
            ACCESS_WRITE,
            JOB_READ,
            JOB_RETRY,
            MAIL_SEND,
            SYSTEM_READ,
            MEDIA_READ,
            MEDIA_CREATE,
            MEDIA_DELETE,
            MEDIA_UPDATE,
            ACCESS_CREATE,
            ACCESS_UPDATE,
            ACCESS_DELETE,
        };
    }

    /** Mã quyền mới bắt buộc có tài nguyên và hành động riêng, không tạo WRITE toàn cục. */
    @JsonCreator
    public static Permission valueOf(String code) {
        if (code == null || code.length() > 40 || code.startsWith("ROLE_") || !code.matches(Regex.PERMISSION_CODE)) {
            throw new IllegalArgumentException("Mã quyền không hợp lệ.");
        }
        for (Permission permission : values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        int split = code.lastIndexOf('_');
        return new Permission(
            code,
            code.substring(0, split),
            Set.of(PermissionAction.valueOf(code.substring(split + 1)))
        );
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Permission permission && code.equals(permission.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
