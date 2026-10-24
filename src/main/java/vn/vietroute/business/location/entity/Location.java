package vn.vietroute.business.location.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Danh mục địa lý giữ ID/audit ổn định; tên/trạng thái tỉnh được JOIN để hiển thị, không ghi vào location_places. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "location_places",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "location_places_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "location_places_code_key", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_location_places_province", columns = { "province_id", "active", "sort_order", "id" }),
        @JdbcIndex(name = "idx_location_places_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_location_places_updated_by", columns = { "updated_by" }),
    }
)
public class Location extends BaseEntity {

    private String code;

    private String nameVi;

    private String nameEn;

    private int sortOrder;

    private boolean active;

    private Long provinceId;

    private String provinceNameVi;

    private String provinceNameEn;

    private boolean provinceActive;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;
}
