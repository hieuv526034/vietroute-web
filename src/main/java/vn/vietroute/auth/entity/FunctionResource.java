package vn.vietroute.auth.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng auth tám bảng, được đối chiếu sau migration trong cùng transaction startup. */
@JdbcTable(name = "auth_function_resources", primaryKey = { "function_code", "kind", "target" })
@Getter
@Setter
@NoArgsConstructor
public class FunctionResource {

    private String functionCode;

    private String kind;

    private String target;

    private String handler;

    private String description;

    private String resourceGroup;

    private String featureCode;

    private Set<String> declaredPermissions;

    private String permissionMatch;

    private boolean adminOnly;

    private boolean publicRoute;

    private boolean configurable;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant createdAt;

    private Instant updatedAt;

    private long createdBy;

    private long updatedBy;

    private long version;
}
