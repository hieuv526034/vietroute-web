package vn.vietroute.business.carrier.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Dữ liệu Carrier nội bộ, scope/audit do server giữ; không trả entity thẳng ra API. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JdbcTable(
    name = "carriers",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carriers_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carriers_code_key", columns = { "code" }, unique = true),
        @JdbcIndex(name = "carriers_scope_id_key", columns = { "scope_id" }, unique = true),
        @JdbcIndex(name = "idx_carriers_active_name", columns = { "active", "name", "id" }, unique = false),
        @JdbcIndex(name = "idx_carriers_logo", columns = { "logo_media_id" }, unique = false),
        @JdbcIndex(name = "idx_carriers_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_carriers_updated_by", columns = { "updated_by" }, unique = false),
    }
)
public class Carrier extends BaseEntity {

    private long scopeId;

    /** Projection từ SELECT guard, không phải cột DB hoặc quyền được client gửi. */
    private boolean canUpdate;
    private String code;
    private String name;
    private String phone;
    private String description;
    private boolean active;
    private Long logoMediaId;
}
