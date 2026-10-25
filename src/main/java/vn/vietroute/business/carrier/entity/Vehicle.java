package vn.vietroute.business.carrier.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Dữ liệu Vehicle nội bộ, scope/audit do server giữ; không trả entity thẳng ra API. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JdbcTable(
    name = "carrier_vehicles",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carrier_vehicles_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carrier_vehicles_plate_key_key", columns = { "plate_key" }, unique = true),
        @JdbcIndex(name = "carrier_vehicles_carrier_id_code_key", columns = { "carrier_id", "code" }, unique = true),
        @JdbcIndex(name = "idx_vehicles_carrier_state", columns = { "carrier_id", "active", "id" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_type", columns = { "vehicle_type_id" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_photo", columns = { "photo_media_id" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_vehicles_updated_by", columns = { "updated_by" }, unique = false),
    }
)
public class Vehicle extends BaseEntity {

    private long carrierId;
    private long scopeId;

    /** Projection từ SELECT guard, không phải cột DB hoặc quyền được client gửi. */
    private boolean canUpdate;
    private String carrierName;
    private String code;
    private String plateNumber;
    private long vehicleTypeId;
    private String vehicleTypeName;
    private int capacity;
    private boolean active;
    private Long photoMediaId;
}
