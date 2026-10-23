package vn.vietroute.auth.entity;

import java.time.Instant;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Metadata persistence của chính sách; projection API được tách riêng để không lộ field mới tự động. */
@JdbcTable(
    name = "auth_access_policies",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_access_policies_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "uq_access_policy_target", columns = { "kind", "target" }, unique = true),
        @JdbcIndex(name = "idx_access_policy_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_access_policy_updated_by", columns = { "updated_by" }),
    }
)
public record AccessPolicy(
    long id,
    String kind,
    String target,
    boolean enabled,
    String requiredPermissions,
    String allowedRoles,
    long version,
    Instant createdAt,
    Instant updatedAt,
    long createdBy,
    long updatedBy
) {}
