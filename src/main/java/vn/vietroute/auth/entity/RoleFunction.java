package vn.vietroute.auth.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng auth tám bảng, được đối chiếu sau migration trong cùng transaction startup. */
@JdbcTable(name = "auth_role_functions", primaryKey = { "role_code", "function_code" })
@Getter
@Setter
@NoArgsConstructor
public class RoleFunction {

    private String roleCode;

    private String functionCode;

    private Set<String> operationCodes;

    private Instant createdAt;

    private Instant updatedAt;

    private long createdBy;

    private long updatedBy;
}
