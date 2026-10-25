package vn.vietroute.business.carrier.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Dữ liệu Driver nội bộ, scope/audit do server giữ; không trả entity thẳng ra API. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JdbcTable(
    name = "carrier_drivers",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_drivers_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carrier_drivers_user_id_key", columns = { "user_id" }, unique = true),
        @JdbcIndex(name = "carrier_drivers_carrier_id_code_key", columns = { "carrier_id", "code" }, unique = true),
        @JdbcIndex(name = "idx_drivers_carrier_state", columns = { "carrier_id", "active", "id" }, unique = false),
        @JdbcIndex(name = "idx_drivers_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_drivers_updated_by", columns = { "updated_by" }, unique = false),
    }
)
public class Driver extends BaseEntity {

    private long carrierId;
    private long scopeId;

    /** Projection từ SELECT guard, không phải cột DB hoặc quyền được client gửi. */
    private boolean canUpdate;
    private boolean canAssign;
    private String carrierName;
    private String code;
    private String name;
    private String phone;
    private Long userId;
    private boolean active;
}
