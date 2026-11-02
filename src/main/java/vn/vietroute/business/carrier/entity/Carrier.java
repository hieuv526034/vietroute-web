package vn.vietroute.business.carrier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Entity đầy đủ cột carriers; migration giữ DDL/index, repo giữ query và transaction do service quản lý. */
@Entity
@Table(
    name = "carriers",
    indexes = {
        @Index(name = "carriers_pkey", columnList = "id", unique = true),
        @Index(name = "carriers_code_key", columnList = "code", unique = true),
        @Index(name = "carriers_scope_id_key", columnList = "scope_id", unique = true),
        @Index(name = "idx_carriers_active_name", columnList = "active, name, id"),
        @Index(name = "idx_carriers_created_by", columnList = "created_by"),
        @Index(name = "idx_carriers_updated_by", columnList = "updated_by"),
    }
)
@JdbcTable(
    name = "carriers",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "carriers_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "carriers_code_key", columns = { "code" }, unique = true),
        @JdbcIndex(name = "carriers_scope_id_key", columns = { "scope_id" }, unique = true),
        @JdbcIndex(name = "idx_carriers_active_name", columns = { "active", "name", "id" }, unique = false),
        @JdbcIndex(name = "idx_carriers_created_by", columns = { "created_by" }, unique = false),
        @JdbcIndex(name = "idx_carriers_updated_by", columns = { "updated_by" }, unique = false),
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Carrier extends BaseEntity {

    // ID phạm vi nhà xe để kiểm quyền trên dữ liệu.
    @Column(name = "scope_id", nullable = false)
    private long scopeId;

    // Mã nghiệp vụ dùng để tra cứu và đối chiếu.
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    // Tên hiển thị.
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // Số điện thoại liên hệ.
    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    // Mô tả bổ sung.
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active", nullable = false)
    private boolean active;

    // Mã phạm vi quản trị riêng của nhà xe.
    @Column(name = "scope_code", nullable = false, length = 50)
    private String scopeCode;

    // Tên phạm vi tiếng Việt.
    @Column(name = "scope_name_vi", nullable = false, length = 120)
    private String scopeNameVi;

    // Tên phạm vi tiếng Anh.
    @Column(name = "scope_name_en", nullable = false, length = 120)
    private String scopeNameEn;

    // Trạng thái bật/tắt phạm vi của nhà xe.
    @Column(name = "scope_active", nullable = false)
    private boolean scopeActive;

    // Phiên bản phạm vi để tránh ghi đè thay đổi gán quyền.
    @Column(name = "scope_version", nullable = false)
    private long scopeVersion;

    /** Projection từ JOIN/guard SQL, không nhận quyền từ client hoặc lưu xuống bảng. */
    @Transient
    private boolean canUpdate;
}
