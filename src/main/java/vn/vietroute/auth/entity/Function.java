package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_functions; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_functions")
@JdbcTable(name = "auth_functions", primaryKey = { "code" })
@Getter
@Setter
@NoArgsConstructor
public class Function {

    @Id
    @Column(name = "code", length = 80)
    private String code;

    @Column(name = "name_vi", length = 500)
    private String nameVi;

    @Column(name = "name_en", length = 500)
    private String nameEn;

    @Column(name = "description", length = 1000)
    private String description = "";

    @Column(name = "parent_code", length = 80)
    private String parentCode;

    @Column(name = "icon", length = 50)
    private String icon = "menu";

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "active")
    private boolean active;

    @Column(name = "deleted")
    private boolean deleted;

    @Column(name = "resources_initialized")
    private boolean resourcesInitialized;

    @Version
    @Column(name = "version")
    private long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "updated_by")
    private long updatedBy;
}
