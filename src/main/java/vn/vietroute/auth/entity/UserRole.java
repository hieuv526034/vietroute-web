package vn.vietroute.auth.entity;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng auth tám bảng, được đối chiếu sau migration trong cùng transaction startup. */
@JdbcTable(name = "auth_user_roles", primaryKey = { "user_id", "role_code", "scope_id" })
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    private long userId;

    private String roleCode;

    private long scopeId;

    private boolean active;

    private long version;

    private Instant createdAt;

    private Instant updatedAt;

    private long createdBy;

    private long updatedBy;
}
