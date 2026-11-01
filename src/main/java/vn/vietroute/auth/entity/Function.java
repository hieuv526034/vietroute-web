package vn.vietroute.auth.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata bảng auth tám bảng, được đối chiếu sau migration trong cùng transaction startup. */
@JdbcTable(name = "auth_functions", primaryKey = { "code" })
@Getter
@Setter
@NoArgsConstructor
public class Function {

    private String code;

    private String nameVi;

    private String nameEn;

    private Set<String> operationCodes;

    private boolean sourceManaged;

    private boolean active;

    private boolean deleted;

    private long registryVersion;

    private Instant createdAt;

    private Instant updatedAt;

    private long createdBy;

    private long updatedBy;
}
