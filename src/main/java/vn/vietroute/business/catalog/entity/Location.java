package vn.vietroute.business.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột location_places; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "location_places",
    indexes = {
        @Index(name = "location_places_pkey", columnList = "id", unique = true),
        @Index(name = "location_places_code_key", columnList = "code", unique = true),
        @Index(name = "idx_location_places_province", columnList = "province_id, active, sort_order, id"),
        @Index(name = "idx_location_places_created_by", columnList = "created_by"),
        @Index(name = "idx_location_places_updated_by", columnList = "updated_by"),
    }
)
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
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Location extends BaseEntity {

    // ID tỉnh/thành trong location_provinces.
    @Column(name = "province_id", nullable = false)
    private Long provinceId;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", nullable = false, length = 120)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    // Địa chỉ điểm đón/trả.
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // Vĩ độ địa điểm tĩnh; không phải GPS thời gian thực.
    @Column(name = "latitude", nullable = false, precision = 8, scale = 6)
    private BigDecimal latitude;

    // Kinh độ địa điểm tĩnh; không phải GPS thời gian thực.
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    // Thứ tự hiển thị tăng dần.
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active", nullable = false)
    private boolean active;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private String provinceNameVi;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private String provinceNameEn;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private boolean provinceActive;
}
