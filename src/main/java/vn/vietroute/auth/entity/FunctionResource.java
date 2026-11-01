package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_function_resources; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_function_resources")
@JdbcTable(name = "auth_function_resources", primaryKey = { "function_code", "kind", "target" })
@Getter
@Setter
@NoArgsConstructor
public class FunctionResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "function_code", length = 80)
    private String functionCode;

    @Column(name = "kind", length = 12)
    private String kind;

    @Column(name = "target", length = 240)
    private String target;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "path_template", length = 240)
    private String pathTemplate;

    @Column(name = "handler", length = 240)
    private String handler;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "name_vi", length = 500)
    private String nameVi;

    @Column(name = "name_en", length = 500)
    private String nameEn;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "sort_order")
    private int sortOrder;

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
