package vn.vietroute.business.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vietroute.common.base.BaseEntity;

/** Một entity cho nhóm và giá trị danh mục; thuộc tính riêng lưu JSON, quan hệ và audit giữ cột có ràng buộc. */
@Entity
@Table(
    name = "category",
    indexes = {
        @Index(name = "category_pkey", columnList = "id", unique = true),
        @Index(name = "category_ref_code", columnList = "ref_type,code", unique = true),
        @Index(name = "idx_category_lookup", columnList = "ref_type,active,sort_order,id"),
        @Index(name = "idx_category_parent", columnList = "ref_type,parent_id,active,sort_order,id"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Category extends BaseEntity {

    // Mã ổn định; GROUP dùng làm định danh nhóm, VALUE duy nhất trong ref_type.
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    // GROUP là định nghĩa, VALUE là giá trị; không suy quyền từ loại bản ghi.
    @Column(name = "type", nullable = false, length = 8)
    private String type;

    // Tên tiếng Việt, tên Anh được giữ trong props.nameEn.
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // Mô tả mục đích hoặc hướng dẫn sử dụng danh mục.
    @Column(name = "description", nullable = false, length = 1000)
    @Builder.Default
    private String description = "";

    // Nhóm danh mục bắt buộc; GROUP tự tham chiếu code, VALUE trỏ GROUP qua FK.
    @Column(name = "ref_type", nullable = false, length = 64)
    private String refType;

    // Quan hệ giá trị cha, ví dụ LOCATION thuộc một PROVINCE.
    @Column(name = "parent_id")
    private Long parentId;

    // Thuộc tính riêng hoặc hợp đồng fields của GROUP; không chứa câu SQL hay quyền từ client.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> props;

    // Cột sinh chỉ đọc làm đích FK của ref_type.
    @Column(name = "group_code", insertable = false, updatable = false, length = 64)
    private String groupCode;

    // Khóa sinh chỉ cho VALUE, FK nghiệp vụ không thể trỏ nhầm bản ghi định nghĩa GROUP.
    @Column(name = "value_ref", insertable = false, updatable = false, length = 64)
    private String valueRef;

    // Ngừng dùng không xóa khóa đã được lịch sử nghiệp vụ tham chiếu.
    @Column(name = "active", nullable = false)
    private boolean active;

    // Thứ tự dùng chung, được index cùng nhóm/cha/trạng thái.
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // Projection đã JOIN từ category cha; không nhận từ request và không lưu lại.
    @Transient
    private String parentName;

    @Transient
    private String parentNameEn;

    @Transient
    private boolean parentActive;

    public Map<String, Object> getProps() {
        if (props == null) props = new LinkedHashMap<>();
        return props;
    }
}
