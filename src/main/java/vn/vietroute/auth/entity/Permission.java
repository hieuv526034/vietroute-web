package vn.vietroute.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
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

    // Các hằng dưới đây là từ vựng chuẩn cho bootstrap/annotation, không phải danh sách quyền hiệu lực của user.
    // Permission.valueOf nhận cả mã tùy chỉnh hợp lệ; UserRepository luôn đọc mapping role/quyền active trong DB.
    // Giữ mã ở source để endpoint mới khai báo hợp đồng rõ ràng; ADMIN gán/thu hồi ở DB mà không sửa các hằng này.

    public static final Permission CATALOG_READ = new Permission(
        Permissions.CATALOG_READ,
        Groups.CATALOG,
        Set.of(PermissionAction.READ)
    );

    public static final Permission CATALOG_CREATE = new Permission(
        Permissions.CATALOG_CREATE,
        Groups.CATALOG,
        Set.of(PermissionAction.CREATE)
    );

    public static final Permission CATALOG_UPDATE = new Permission(
        Permissions.CATALOG_UPDATE,
        Groups.CATALOG,
        Set.of(PermissionAction.UPDATE)
    );

    public static final Permission CATALOG_DELETE = new Permission(
        Permissions.CATALOG_DELETE,
        Groups.CATALOG,
        Set.of(PermissionAction.DELETE)
    );

    // Giữ mã cũ để không làm mất quyền role đang dùng: WRITE chỉ gồm tạo/sửa, không ngầm được đọc/xóa/gán.
    public static final Permission CATALOG_WRITE = new Permission(
        Permissions.CATALOG_WRITE,
        Groups.CATALOG,
        Set.of(PermissionAction.CREATE, PermissionAction.UPDATE)
    );

    public static final Permission ACCESS_READ = new Permission(
        Permissions.ACCESS_READ,
        Groups.ACCESS,
        Set.of(PermissionAction.READ)
    );

    public static final Permission ACCESS_ASSIGN = new Permission(
        Permissions.ACCESS_ASSIGN,
        Groups.ACCESS,
        Set.of(PermissionAction.ASSIGN)
    );

    // ACCESS_WRITE là mã tương thích cũ cho thao tác gán; API mới hướng dẫn dùng ACCESS_ASSIGN.
    public static final Permission ACCESS_WRITE = new Permission(
        Permissions.ACCESS_WRITE,
        Groups.ACCESS,
        Set.of(PermissionAction.ASSIGN)
    );

    public static final Permission JOB_READ = new Permission(
        Permissions.JOB_READ,
        Groups.JOB,
        Set.of(PermissionAction.READ)
    );

    public static final Permission JOB_RETRY = new Permission(
        Permissions.JOB_RETRY,
        Groups.JOB,
        Set.of(PermissionAction.RETRY)
    );

    public static final Permission MAIL_SEND = new Permission(
        Permissions.MAIL_SEND,
        Groups.MAIL,
        Set.of(PermissionAction.SEND)
    );

    public static final Permission SYSTEM_READ = new Permission(
        Permissions.SYSTEM_READ,
        Groups.SYSTEM,
        Set.of(PermissionAction.READ)
    );

    public static final Permission MEDIA_READ = new Permission(
        Permissions.MEDIA_READ,
        Groups.MEDIA,
        Set.of(PermissionAction.READ)
    );

    public static final Permission MEDIA_CREATE = new Permission(
        Permissions.MEDIA_CREATE,
        Groups.MEDIA,
        Set.of(PermissionAction.CREATE)
    );

    public static final Permission MEDIA_DELETE = new Permission(
        Permissions.MEDIA_DELETE,
        Groups.MEDIA,
        Set.of(PermissionAction.DELETE)
    );

    public static final Permission MEDIA_UPDATE = new Permission(
        Permissions.MEDIA_UPDATE,
        Groups.MEDIA,
        Set.of(PermissionAction.UPDATE)
    );

    public static final Permission ACCESS_CREATE = new Permission(
        Permissions.ACCESS_CREATE,
        Groups.ACCESS,
        Set.of(PermissionAction.CREATE)
    );

    public static final Permission ACCESS_UPDATE = new Permission(
        Permissions.ACCESS_UPDATE,
        Groups.ACCESS,
        Set.of(PermissionAction.UPDATE)
    );

    public static final Permission ACCESS_DELETE = new Permission(
        Permissions.ACCESS_DELETE,
        Groups.ACCESS,
        Set.of(PermissionAction.DELETE)
    );

    public static final Permission CATALOG_VIEW = new Permission(
        Permissions.CATALOG_VIEW,
        "CATALOG",
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission MEDIA_VIEW = new Permission(
        Permissions.MEDIA_VIEW,
        "MEDIA",
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission ACCESS_VIEW = new Permission(
        Permissions.ACCESS_VIEW,
        "ACCESS",
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission JOB_VIEW = new Permission(
        Permissions.JOB_VIEW,
        "JOB",
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission SYSTEM_VIEW = new Permission(
        Permissions.SYSTEM_VIEW,
        "SYSTEM",
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission CONFIGURATION_VIEW = new Permission(
        Permissions.CONFIGURATION_VIEW,
        Groups.CONFIGURATION,
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission CONFIGURATION_READ = new Permission(
        Permissions.CONFIGURATION_READ,
        Groups.CONFIGURATION,
        Set.of(PermissionAction.READ)
    );

    public static final Permission JOURNAL_VIEW = new Permission(
        Permissions.JOURNAL_VIEW,
        Groups.JOURNAL,
        Set.of(PermissionAction.VIEW)
    );

    public static final Permission ACCOUNT_VIEW = new Permission(
        Permissions.ACCOUNT_VIEW,
        Groups.ACCOUNTS,
        Set.of(PermissionAction.VIEW)
    );
    public static final Permission ACCOUNT_READ = new Permission(
        Permissions.ACCOUNT_READ,
        Groups.ACCOUNTS,
        Set.of(PermissionAction.READ)
    );
    public static final Permission ACCOUNT_CREATE = new Permission(
        Permissions.ACCOUNT_CREATE,
        Groups.ACCOUNTS,
        Set.of(PermissionAction.CREATE)
    );
    public static final Permission ACCOUNT_UPDATE = new Permission(
        Permissions.ACCOUNT_UPDATE,
        Groups.ACCOUNTS,
        Set.of(PermissionAction.UPDATE)
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

    /** Chỉ danh mục bootstrap/tương thích; tuyệt đối không dùng values() để suy quyền của tài khoản hiện tại. */
    public static Permission[] values() {
        return new Permission[] {
            ACCOUNT_VIEW,
            ACCOUNT_READ,
            ACCOUNT_CREATE,
            ACCOUNT_UPDATE,
            CATALOG_VIEW,
            MEDIA_VIEW,
            ACCESS_VIEW,
            JOB_VIEW,
            SYSTEM_VIEW,
            CONFIGURATION_VIEW,
            CONFIGURATION_READ,
            JOURNAL_VIEW,
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
