package vn.vietroute.auth.entity;

import java.util.Set;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTable;

/** Quyền là hành động cụ thể; role chỉ gom quyền, không được dùng role để bỏ qua phạm vi dữ liệu. */
@JdbcTable(
    schema = JdbcSchema.NAME,
    name = "auth_permissions",
    primaryKey = { "code" },
    indexes = { @JdbcIndex(name = "auth_permissions_pkey", columns = { "code" }, unique = true) }
)
public enum Permission {
    CATALOG_WRITE(Set.of(Role.ADMIN)),
    ACCESS_READ(Set.of(Role.ADMIN)),
    ACCESS_WRITE(Set.of(Role.ADMIN)),
    JOB_READ(Set.of(Role.ADMIN)),
    JOB_RETRY(Set.of(Role.ADMIN)),
    MAIL_SEND(Set.of(Role.ADMIN)),
    SYSTEM_READ(Set.of(Role.ADMIN));

    private final Set<Role> defaults;

    Permission(Set<Role> defaults) {
        this.defaults = defaults;
    }

    public String messageKey() {
        return "permission." + name();
    }

    public boolean grantedInitially(Role role) {
        return defaults.contains(role);
    }
}
